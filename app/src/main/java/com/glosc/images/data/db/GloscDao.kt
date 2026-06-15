package com.glosc.images.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GloscDao {
    @Query("SELECT * FROM image_assets ORDER BY createdAt DESC")
    fun observeImages(): Flow<List<ImageAssetEntity>>

    @Query("SELECT * FROM image_assets WHERE id = :id LIMIT 1")
    suspend fun getImage(id: String): ImageAssetEntity?

    @Query("SELECT * FROM image_assets ORDER BY createdAt DESC")
    suspend fun getImagesOnce(): List<ImageAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImage(asset: ImageAssetEntity)

    @Query("UPDATE image_assets SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE image_assets SET tags = :tags, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTags(id: String, tags: String, updatedAt: Long)

    @Query("DELETE FROM image_assets WHERE id = :id")
    suspend fun deleteImage(id: String)

    @Query("SELECT * FROM generation_tasks ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentTasks(limit: Int = 12): Flow<List<GenerationTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: GenerationTaskEntity)

    @Query(
        """
        UPDATE generation_tasks
        SET status = :status,
            errorCode = :errorCode,
            errorMessage = :errorMessage,
            imageAssetId = :imageAssetId,
            finishedAt = :finishedAt
        WHERE id = :id
        """
    )
    suspend fun finishTask(
        id: String,
        status: String,
        errorCode: String?,
        errorMessage: String?,
        imageAssetId: String?,
        finishedAt: Long
    )

    @Query("SELECT * FROM api_providers ORDER BY enabled DESC, name ASC")
    fun observeProviders(): Flow<List<ApiProviderEntity>>

    @Query("SELECT * FROM api_providers ORDER BY enabled DESC, name ASC")
    suspend fun getProvidersOnce(): List<ApiProviderEntity>

    @Query("SELECT * FROM api_providers WHERE enabled = 1 LIMIT 1")
    suspend fun getActiveProvider(): ApiProviderEntity?

    @Query("SELECT * FROM api_providers WHERE id = :id LIMIT 1")
    suspend fun getProvider(id: String): ApiProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: ApiProviderEntity)

    @Query("UPDATE api_providers SET enabled = 0")
    suspend fun disableAllProviders()

    @Query("UPDATE api_providers SET lastTestedAt = :testedAt, lastStatus = :status WHERE id = :id")
    suspend fun updateProviderStatus(id: String, testedAt: Long, status: String)

    @Query(
        """
        UPDATE api_providers
        SET defaultModel = :defaultModel,
            imageModels = :imageModels,
            lastTestedAt = :testedAt,
            lastStatus = :status
        WHERE id = :id
        """
    )
    suspend fun updateProviderModels(
        id: String,
        defaultModel: String,
        imageModels: String,
        testedAt: Long,
        status: String
    )

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearMessages(conversationId: String)
}
