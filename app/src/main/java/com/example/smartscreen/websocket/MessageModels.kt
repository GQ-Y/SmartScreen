package com.example.smartscreen.websocket

import com.google.gson.annotations.SerializedName

/**
 * Base class for all WebSocket messages
 */
open class BaseMessage(
    @SerializedName("type") val type: String
)

/**
 * Message for device registration
 * Client -> Server
 */
data class RegisterMessage(
    @SerializedName("mac") val mac: String,
    @SerializedName("device_name") val deviceName: String
) : BaseMessage("register")

/**
 * Message for heartbeat
 * Client -> Server
 */
data class HeartbeatMessage(
    @SerializedName("mac") val mac: String
) : BaseMessage("heartbeat")

/**
 * Message to get content
 * Client -> Server
 */
data class GetContentMessage(
    @SerializedName("mac") val mac: String
) : BaseMessage("get_content")


// --- Server -> Client ---

/**
 * Register acknowledgement message
 * Server -> Client
 */
data class RegisterAckMessage(
    @SerializedName("success") val success: Boolean,
    @SerializedName("active") val active: Int,
    @SerializedName("device_id") val deviceId: Long,
    @SerializedName("is_new_device") val isNewDevice: Boolean,
    @SerializedName("msg") val msg: String
) : BaseMessage("register_ack")

/**
 * Content response message
 * Server -> Client
 */
data class ContentResponseMessage(
    @SerializedName("success") val success: Boolean,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: ContentData
) : BaseMessage("content_response")

data class ContentData(
    @SerializedName("device_id") val deviceId: Long,
    @SerializedName("display_mode") val displayMode: Int,
    @SerializedName("display_mode_name") val displayModeName: String,
    @SerializedName("direct_content") val directContent: Content?,
    @SerializedName("playlist_contents") val playlistContents: List<PlaylistContent>,
    @SerializedName("primary_contents") val primaryContents: List<PlaylistContent>,
    @SerializedName("secondary_contents") val secondaryContents: List<PlaylistContent>
)

data class Content(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("content_type") val contentType: Any, // Can be String or Double from JSON
    @SerializedName("content_url") val contentUrl: String,
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("duration") val duration: Double // 改为Double类型以匹配JSON
)

data class PlaylistContent(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("content_type") val contentType: Any, // Can be String or Double from JSON
    @SerializedName("content_url") val contentUrl: String,
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("duration") val duration: Double, // 改为Double类型以匹配JSON
    @SerializedName("playlist_id") val playlistId: Long,
    @SerializedName("playlist_name") val playlistName: String,
    @SerializedName("play_mode") val playMode: Int,
    @SerializedName("playlist_sort") val playlistSort: Int,
    @SerializedName("content_sort") val contentSort: Int
)

/**
 * Heartbeat acknowledgement message
 * Server -> Client
 */
data class HeartbeatAckMessage(
    @SerializedName("success") val success: Boolean,
    @SerializedName("active") val active: Int,
    @SerializedName("msg") val msg: String
) : BaseMessage("heartbeat_ack")

/**
 * Device activation status change message
 * Server -> Client
 */
data class ActiveStatusMessage(
    @SerializedName("active") val active: Boolean,
    @SerializedName("msg") val msg: String
) : BaseMessage("active_status")

/**
 * Server push content message
 * Server -> Client
 */
data class PushContentMessage(
    @SerializedName("data") val data: Content
) : BaseMessage("push_content")

/**
 * Display mode change message
 * Server -> Client
 */
data class DisplayModeChangeMessage(
    @SerializedName("mode") val mode: Int,
    @SerializedName("mode_name") val modeName: String
) : BaseMessage("display_mode_change")

/**
 * Temporary content push message
 * Server -> Client
 */
data class TempContentMessage(
    @SerializedName("data") val data: TempContentData
) : BaseMessage("temp_content")

data class TempContentData(
    @SerializedName("content_id") val contentId: Long,
    @SerializedName("content_type") val contentType: String,
    @SerializedName("content_url") val contentUrl: String,
    @SerializedName("title") val title: String,
    @SerializedName("duration") val duration: Double, // 改为Double类型以匹配JSON
    @SerializedName("thumbnail") val thumbnail: String?,
    @SerializedName("is_temp") val isTemp: Boolean
)

/**
 * Batch control command message
 * Server -> Client
 */
data class BatchControlMessage(
    @SerializedName("action") val action: String, // "restart", "shutdown"
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: Long
) : BaseMessage("batch_control")

/**
 * System refresh message
 * Server -> Client
 */
data class RefreshMessage(
    @SerializedName("message") val message: String
) : BaseMessage("refresh")

/**
 * Error message
 * Server -> Client
 */
data class ErrorMessage(
    @SerializedName("msg") val msg: String
) : BaseMessage("error") 