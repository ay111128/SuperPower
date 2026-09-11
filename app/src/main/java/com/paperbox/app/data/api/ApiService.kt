package com.paperbox.app.data.api

import com.paperbox.app.data.api.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── 认证 ──

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    // ── 素材管理 ──

    @GET("materials-api/materials")
    suspend fun getMaterials(
        @Query("color") color: String? = null,
        @Query("tags") tags: String? = null,
        @Query("online") online: String? = null,
        @Query("q") query: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<MaterialListResponse>

    @Multipart
    @POST("materials-api/materials")
    suspend fun uploadMaterials(
        @Part files: List<MultipartBody.Part>,
        @Part("color") color: RequestBody? = null
    ): Response<UploadResponse>

    @PATCH("materials-api/materials/{id}")
    suspend fun updateMaterial(
        @Path("id") id: String,
        @Body body: Map<String, Any>
    ): Response<MaterialItem>

    @DELETE("materials-api/materials/{id}")
    suspend fun deleteMaterial(@Path("id") id: String): Response<Map<String, Boolean>>

    @GET("materials-api/materials/{id}/file")
    suspend fun getMaterialFile(@Path("id") id: String): Response<okhttp3.ResponseBody>

    @GET("materials-api/materials/tags")
    suspend fun getTags(): Response<TagsResponse>

    @GET("materials-api/materials/colors")
    suspend fun getColors(): Response<ColorsResponse>

    @GET("materials-api/materials/color-counts")
    suspend fun getColorCounts(
        @Query("tags") tags: String? = null,
        @Query("online") online: String? = null
    ): Response<ColorCountsResponse>

    @POST("materials-api/materials/scan-duplicates")
    suspend fun scanDuplicates(): Response<Map<String, Any>>

    // ── 报价记录 ──

    @POST("materials-api/quote-records")
    suspend fun saveQuoteRecord(@Body record: QuoteRecordRequest): Response<QuoteRecordResponse>

    // ── 现货产品 ──

    @GET("pricing-api/spot-products")
    suspend fun getSpotProducts(): Response<List<SpotProduct>>

    @GET("pricing-api/spot-products/{category}")
    suspend fun getSpotProductsByCategory(
        @Path("category") category: String
    ): Response<List<SpotProduct>>

    // ── 材质配置 ──

    @GET("pricing-api/material-configs")
    suspend fun getMaterialConfigs(): Response<List<MaterialConfig>>

    // ── 工艺配置 ──

    @GET("pricing-api/process-configs")
    suspend fun getProcessConfigs(): Response<List<ProcessConfig>>
}
