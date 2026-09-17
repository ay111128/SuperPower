#!/usr/bin/env bash
# Paperbox Android 一键部署脚本
# 流程：检查修改 → 升版本号 → 提交 → 推送 GitHub → 轮询构建结果
set -euo pipefail

GITHUB_REPO="ay111128/SuperPower"
GIT_CREDENTIALS="/home/ubuntu/.git-credentials"

# ========== 辅助函数 ==========

# 从 git-credentials 提取 GitHub token
get_github_token() {
  grep -oP 'ghp_[^@]+' "$GIT_CREDENTIALS" 2>/dev/null || {
    echo "❌ 无法读取 GitHub token，请检查 $GIT_CREDENTIALS"
    exit 1
  }
}

# 自动升版本号：versionCode +1, patch 版本 +1
bump_version() {
  local gradle_file="app/build.gradle.kts"
  local vc vn

  vc=$(grep -oP 'versionCode = \K\d+' "$gradle_file")
  vn=$(grep -oP 'versionName = "\K[^"]+' "$gradle_file")

  local new_vc=$((vc + 1))
  local major minor patch
  IFS='.' read -r major minor patch <<< "$vn"
  patch=$((patch + 1))
  local new_vn="${major}.${minor}.${patch}"

  sed -i "s/versionCode = $vc/versionCode = $new_vc/" "$gradle_file"
  sed -i "s/versionName = \"$vn\"/versionName = \"$new_vn\"/" "$gradle_file"

  echo "$new_vn"
}

# 轮询 GitHub Actions 构建结果
poll_build() {
  local token
  token=$(get_github_token)
  local max_attempts=60  # 最多等 60 次（约 5 分钟）
  local attempt=0

  echo ""
  echo "⏳ 轮询构建状态..."

  while [ $attempt -lt $max_attempts ]; do
    attempt=$((attempt + 1))
    sleep 30

    local response
    response=$(curl -s -H "Authorization: token $token" \
      "https://api.github.com/repos/$GITHUB_REPO/actions/runs?per_page=1")

    local status conclusion sha
    status=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['workflow_runs'][0]['status'])" 2>/dev/null || echo "unknown")
    conclusion=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['workflow_runs'][0]['conclusion'] or '')" 2>/dev/null || echo "")
    sha=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['workflow_runs'][0]['head_sha'][:8])" 2>/dev/null || echo "unknown")

    if [ "$status" = "completed" ]; then
      if [ "$conclusion" = "success" ]; then
        echo ""
        echo "✅ 构建成功！"
        echo "   SHA: $sha"
        echo "   下载: https://github.com/$GITHUB_REPO/releases/latest"
        return 0
      else
        echo ""
        echo "❌ 构建失败 (conclusion: $conclusion)"
        echo "   SHA: $sha"
        echo "   详情: https://github.com/$GITHUB_REPO/actions"
        return 1
      fi
    fi

    printf "\r   [%d/%d] 状态: %s (SHA: %s)" "$attempt" "$max_attempts" "$status" "$sha"
  done

  echo ""
  echo "⏰ 轮询超时（${max_attempts}次），请手动检查: https://github.com/$GITHUB_REPO/actions"
  return 1
}

# ========== 主流程 ==========

echo "🚀 Paperbox Android 部署"
echo "========================"

# 第1步：检查修改
echo ""
echo "📝 [1/4] 检查本地修改..."
if git diff --quiet && git diff --cached --quiet; then
  echo "   ✅ 无未提交修改"
else
  echo "   发现修改，将自动提交..."
fi

# 第2步：升版本号
echo ""
echo "📦 [2/4] 升版本号..."
NEW_VERSION=$(bump_version)
echo "   ✅ 版本号已更新: $NEW_VERSION"

# 第3步：提交并推送
echo ""
echo "📤 [3/4] 提交并推送到 GitHub..."
git add -A
CHANGED_FILES=$(git diff --cached --name-only | head -5 | xargs -I {} basename {} | tr '\n' ', ' | sed 's/,$//')
COMMIT_MSG="release: v${NEW_VERSION} - ${CHANGED_FILES}"
git commit -m "$COMMIT_MSG

Co-Authored-By: Claude Code <noreply@anthropic.com>"

if ! git push github master; then
  echo "   ❌ 推送到 GitHub 失败"
  exit 1
fi
echo "   ✅ 已推送到 GitHub，构建已触发"

# 同步推送到 Gitee（如果配置了 origin 远端）
if git remote get-url origin &>/dev/null; then
  echo "   📤 同步推送到 Gitee..."
  git push origin master || echo "   ⚠️  Gitee 推送失败（不影响构建）"
else
  echo "   ⏭️  未配置 Gitee 远端，跳过"
fi

# 第4步：轮询构建
echo ""
echo "🔍 [4/4] 等待 GitHub Actions 构建..."
poll_build
