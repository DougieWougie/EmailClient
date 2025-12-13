package com.emailclient.domain.model

enum class SwipeAction(val displayName: String) {
    ARCHIVE("Archive"),
    DELETE("Delete"),
    MARK_READ("Mark as Read/Unread"),
    NONE("None (Disabled)");

    companion object {
        fun fromOrdinal(ordinal: Int): SwipeAction {
            return values().getOrNull(ordinal) ?: NONE
        }
    }
}
