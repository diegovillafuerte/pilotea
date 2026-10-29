package mx.kompara.data.db.dao

import androidx.room.*
import mx.kompara.data.imports.*

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_drafts WHERE id = :id") suspend fun draft(id: String): ImportDraftEntity?
    @Query("SELECT * FROM import_drafts WHERE stage NOT IN ('SAVED', 'DISCARDED', 'SUPERSEDED') ORDER BY createdAt DESC, rowid DESC LIMIT 1")
    suspend fun pending(): ImportDraftEntity?
    @Upsert suspend fun putDraft(draft: ImportDraftEntity)
    @Upsert suspend fun putFiles(files: List<ImportFileEntity>)
    @Query("SELECT * FROM import_files WHERE draftId = :id ORDER BY position") suspend fun files(id: String): List<ImportFileEntity>
    @Upsert suspend fun putStatement(statement: ImportStatementEntity)
    @Query("SELECT * FROM import_statements ORDER BY savedAt, id") suspend fun statements(): List<ImportStatementEntity>
    @Upsert suspend fun putSelection(selection: ImportSelectionEntity)
    @Query("SELECT * FROM import_selections") suspend fun selections(): List<ImportSelectionEntity>
    @Query("DELETE FROM import_files WHERE draftId = :id") suspend fun removeFiles(id: String)
}
