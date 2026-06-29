package com.nzr.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseDataResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class ListData<T>(
    val items: List<T> = emptyList(),
    val list: List<T> = emptyList(),
    val pager: Pager? = null
) {
    val all: List<T> get() = items.ifEmpty { list }
}

@JsonClass(generateAdapter = true)
data class TrendingItem(
    val type: String? = null,
    val title: String? = null,
    val subjects: List<ContentItem> = emptyList(),
    val banner: BannerHolder? = null
) {
    val allSubjects: List<ContentItem>
        get() {
            val list = mutableListOf<ContentItem>()
            list.addAll(subjects)
            banner?.banners?.forEach { it.subject?.let { s -> list.add(s) } }
            banner?.items?.forEach { it.subject?.let { s -> list.add(s) } }
            return list
        }
}

@JsonClass(generateAdapter = true)
data class BannerHolder(
    val banners: List<BannerItem> = emptyList(),
    val items: List<BannerItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BannerItem(
    val subject: ContentItem? = null
)

@JsonClass(generateAdapter = true)
data class TestLiveItemResponse(
    val success: Boolean = false,
    val id: String? = null,
    val subject: ContentItem? = null,
    val streams: List<ResolutionItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SubjectData(
    val subject: ContentItem? = null
)

@JsonClass(generateAdapter = true)
data class PaxSenixSearchResponse(
    val ok: Boolean = false,
    val pager: Pager? = null,
    val results: List<SearchResultGroup> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PaxSenixTrendingResponse(
    val ok: Boolean = false,
    val pager: Pager? = null,
    val items: List<ContentItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Pager(
    val page: String = "1",
    val next_page: String = "2",
    val per_page: Int = 20,
    val has_more: Boolean = false,
    val total_count: Int = 0
)

@JsonClass(generateAdapter = true)
data class SearchResultGroup(
    val topic_type: String? = null,
    val title: String? = null,
    val subjects: List<ContentItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ContentItem(
    @Json(name = "subjectId") val subject_id: String? = null,
    @Json(name = "subjectType") val subject_type: Int? = null, // 1=Movie, 2=TV
    val title: String? = null,
    val description: String? = null,
    @Json(name = "releaseDate") val release_date: String? = null,
    val duration: String? = null,
    @Json(name = "durationSeconds") val duration_seconds: Int? = null,
    val genre: String? = null,
    @Json(name = "countryName") val country_name: String? = null,
    val language: String? = null,
    @Json(name = "imdbRatingValue") val imdb_rating_value: String? = null,
    @Json(name = "contentRating") val content_rating: String? = null,
    val corner: String? = null,
    @Json(name = "coverImageUrl") val cover_url: String? = null,
    @Json(name = "hasResource") val has_resource: Boolean? = null,
    @Json(name = "detailPath") val detail_url: String? = null,
    val viewers: Int? = null,
    @Json(name = "seNum") val se_num: Int? = null,
    val season: Int? = null,
    @Json(name = "isCam") val is_cam: Boolean? = null,
    val aka: String? = null,
    val subtitles: String? = null,
    val skills: Stills? = null,
    val stills: Stills? = null,
    @Json(name = "resourceDetectors") val resource_detectors: List<ResourceDetector> = emptyList(),
    val dubs: List<DubInfo> = emptyList(),
    @Json(name = "staffList") val staff_list: List<StaffList> = emptyList(),
    // Added for backward compatibility in UI layer
    val type: String? = null, // Can map logic manually in repository if needed
    @Json(name = "typeId") val type_id: Int? = null,
    @Json(name = "imdbRating") val imdb_rating: String? = null,
    val seasons: List<SeasonInfo>? = null,
    @Json(name = "totalSeasons") val total_seasons: Int? = null,
    val cover: CoverInfo? = null
) {
    val ratingValue: Double?
        get() = imdb_rating_value?.toDoubleOrNull()
    
    val coverUrlResolved: String? 
        get() = cover?.url ?: cover_url
    
    val year: String?
        get() = release_date?.take(4)
}

@JsonClass(generateAdapter = true)
data class CoverInfo(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class SeasonInfo(
    val season: Int? = null,
    val se: Int? = null,
    @Json(name = "episode_count") val episode_count: Int? = null,
    val maxEp: Int? = null,
    @Json(name = "available_resolutions") val available_resolutions: List<Int> = emptyList(),
    @Json(name = "quality_labels") val quality_labels: List<String> = emptyList(),
    @Json(name = "episodes_url") val episodes_url: String? = null
) {
    val seasonNumber: Int get() = season ?: se ?: 1
    val count: Int get() = episode_count ?: maxEp ?: 1
}

@JsonClass(generateAdapter = true)
data class Stills(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val format: String? = null,
    val size: Int? = null,
    val thumbnail: String? = null
)

@JsonClass(generateAdapter = true)
data class ResourceDetector(
    val resource_id: String? = null,
    val subject_id: String? = null,
    val type: Int? = null,
    val total_episode: Int? = null,
    val source: String? = null,
    val resource_link: String? = null,
    val download_url: String? = null,
    val codec_name: String? = null,
    val resolution_list: List<ResolutionItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ResolutionItem(
    @Json(name = "resourceId") val resource_id: String? = null,
    @Json(name = "postId") val post_id: String? = null,
    val episode: Int = 0,
    val se: Int = 0,
    val ep: Int = 0,
    val title: String? = null,
    @Json(name = "resourceLink") val resource_link: String? = null,
    @Json(name = "linkType") val link_type: Int? = null,
    val resolution: Int? = null,
    @Json(name = "codecName") val codec_name: String? = null,
    val duration: Int? = null,
    val size: String? = null,
    @Json(name = "uploadBy") val upload_by: String? = null,
    @Json(name = "sourceUrl") val source_url: String? = null,
    @Json(name = "requireMemberType") val require_member_type: Int = 0
)

@JsonClass(generateAdapter = true)
data class DubInfo(
    val subject_id: String? = null,
    val lan_name: String? = null,
    val lan_code: String? = null,
    val original: Boolean? = null,
    val type: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenreResponse(
    val type: String = "all",
    val genres: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StaffList(
    @Json(name = "staffId") val staff_id: String? = null,
    @Json(name = "staffType") val staff_type: Int? = null,
    val name: String? = null,
    val character: String? = null,
    @Json(name = "avatarUrl") val avatar_url: String? = null
)

// Legacy classes to satisfy compilation temporarily, will map internally

@JsonClass(generateAdapter = true)
data class HomeResponse(
    val sections: List<HomeSection>? = null
)

@JsonClass(generateAdapter = true)
data class HomeSection(
    val id: String = "",
    val title: String = "",
    val items: List<ContentItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SearchResponse(
    val query: String? = null,
    val type: String? = null,
    val page: Int? = null,
    @Json(name = "has_more") val has_more: Boolean? = null,
    val total: Int? = null,
    val results: List<ContentItem>? = null
)

@JsonClass(generateAdapter = true)
data class BrowseResponse(
    val page: Int? = null,
    @Json(name = "has_more") val has_more: Boolean? = null,
    val total: Int? = null,
    val results: List<ContentItem>? = null
)

@JsonClass(generateAdapter = true)
data class WatchResponse(
    @Json(name = "subject_id") val subject_id: String = "",
    val title: String = "",
    val type: String = "",
    val season: Int = 0,
    val episode: Int = 1,
    @Json(name = "episode_count") val episode_count: Int = 1,
    @Json(name = "total_seasons") val total_seasons: Int = 1,
    @Json(name = "available_resolutions") val available_resolutions: List<Int> = emptyList(),
    @Json(name = "all_seasons") val all_seasons: List<SeasonInfo> = emptyList(),
    val metadata: ContentItem? = null,
    val episodes: List<Episode> = emptyList(),
    val stream: StreamResponse? = null,
    val related: List<ContentItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Episode(
    val episode: Int,
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String = "",
    @Json(name = "dash_url") val dash_url: String = "",
    @Json(name = "captions_url") val captions_url: String = ""
)

@JsonClass(generateAdapter = true)
data class StreamResponse(
    @Json(name = "subject_id") val subject_id: String = "",
    @Json(name = "has_resource") val has_resource: Boolean = false,
    @Json(name = "best_url") val best_url: String? = null,
    @Json(name = "best_format") val best_format: String? = null,
    val streams: List<StreamSource> = emptyList(),
    val dash: List<DashSource> = emptyList(),
    val hls: List<HlsSource> = emptyList(),
    @Json(name = "captions_url") val captions_url: String? = null,
    @Json(name = "all_qualities_url") val all_qualities_url: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamSource(
    val url: String = "",
    val quality: Int = 0,
    @Json(name = "quality_label") val quality_label: String = "",
    val format: String = "",
    val codec: String = "",
    @Json(name = "resource_id") val resource_id: String = ""
)

@JsonClass(generateAdapter = true)
data class DashSource(
    val url: String = ""
)

@JsonClass(generateAdapter = true)
data class HlsSource(
    val url: String = ""
)
