package com.nzr.api

import com.nzr.api.models.*
import retrofit2.http.GET
import retrofit2.http.Query

interface MovieBoxService {
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("type") type: Int = 0
    ): BaseDataResponse<ListData<ContentItem>>

    @GET("api/trending")
    suspend fun getTrending(
        @Query("page") page: Int = 1
    ): BaseDataResponse<ListData<TrendingItem>>

    @GET("api/trending/movies")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1
    ): BaseDataResponse<ListData<TrendingItem>>

    @GET("api/trending/series")
    suspend fun getTrendingSeries(
        @Query("page") page: Int = 1
    ): BaseDataResponse<ListData<TrendingItem>>

    @GET("api/test-live/item")
    suspend fun getTestLiveItem(
        @Query("id") id: String,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null
    ): TestLiveItemResponse

    @GET("api/movie/details")
    suspend fun getInfo(
        @Query("id") id: String
    ): BaseDataResponse<SubjectData>

    @GET("api/series/details")
    suspend fun getSeriesDetails(
        @Query("id") id: String,
        @Query("season") season: Int = 1
    ): BaseDataResponse<SubjectData>
    
    @GET("api/episode/resource")
    suspend fun getEpisodeResource(
        @Query("id") id: String,
        @Query("season") season: Int = 0,
        @Query("episode") episode: Int? = null
    ): BaseDataResponse<ListData<ResolutionItem>>
}
