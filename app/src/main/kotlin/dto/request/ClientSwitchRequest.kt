package dto.request

import kotlinx.serialization.Serializable

@Serializable
data class ClientSwitchRequest(val clientName: String)
