package com.emailclient.data.local

import androidx.room.TypeConverter
import com.emailclient.domain.model.EmailAddress
import com.emailclient.domain.model.Attachment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

/**
 * Type converters for Room database
 */
class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromEmailAddress(value: EmailAddress?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toEmailAddress(value: String?): EmailAddress? {
        return value?.let { gson.fromJson(it, EmailAddress::class.java) }
    }

    @TypeConverter
    fun fromEmailAddressList(value: List<EmailAddress>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toEmailAddressList(value: String?): List<EmailAddress>? {
        val listType = object : TypeToken<List<EmailAddress>>() {}.type
        return value?.let { gson.fromJson(it, listType) }
    }

    @TypeConverter
    fun fromAttachmentList(value: List<Attachment>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toAttachmentList(value: String?): List<Attachment>? {
        val listType = object : TypeToken<List<Attachment>>() {}.type
        return value?.let { gson.fromJson(it, listType) }
    }
}
