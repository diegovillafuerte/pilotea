package mx.kompara.sync.imports

import androidx.room.withTransaction
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.imports.*
import javax.inject.Inject

interface LocalImportStore {
    suspend fun draft(id: String): ImportDraftEntity?
    suspend fun pending(): ImportDraftEntity?
    suspend fun files(id: String): List<ImportFileEntity>
    suspend fun retain(draft: ImportDraftEntity, files: List<ImportFileEntity>)
    suspend fun update(draft: ImportDraftEntity)
    suspend fun commit(draft: ImportDraftEntity, statement: ImportStatementEntity, selection: ImportSelectionEntity?)
    suspend fun discard(id: String)
}

class RoomLocalImportStore @Inject constructor(private val db: KomparaDatabase) : LocalImportStore {
    private val dao get() = db.importDao()
    override suspend fun draft(id: String) = dao.draft(id)
    override suspend fun pending() = dao.pending()
    override suspend fun files(id: String) = dao.files(id)
    override suspend fun retain(draft: ImportDraftEntity, files: List<ImportFileEntity>) = db.withTransaction {
        dao.putDraft(draft); dao.putFiles(files)
    }
    override suspend fun update(draft: ImportDraftEntity) = db.withTransaction {
        check(dao.draft(draft.id) != null) // never resurrect a draft erased during a network read
        dao.putDraft(draft)
    }
    override suspend fun commit(draft: ImportDraftEntity, statement: ImportStatementEntity, selection: ImportSelectionEntity?) = db.withTransaction {
        if (dao.draft(draft.id)?.stage == "SAVED") return@withTransaction
        check(dao.draft(draft.id) != null) // a local-data erase must not be undone by a stale screen
        dao.putStatement(statement)
        selection?.let { dao.putSelection(it) }
        dao.putDraft(draft.copy(stage = "SAVED"))
        dao.removeFiles(draft.id)
        draft.parentId?.let { parent ->
            dao.draft(parent)?.let { dao.putDraft(it.copy(stage = "SUPERSEDED")) }
            dao.removeFiles(parent)
        }
        Unit
    }
    override suspend fun discard(id: String) = db.withTransaction {
        dao.draft(id)?.let { dao.putDraft(it.copy(stage = "DISCARDED")) }
        dao.removeFiles(id)
    }
}
