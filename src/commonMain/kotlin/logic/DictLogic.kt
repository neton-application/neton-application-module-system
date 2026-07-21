package logic

import controller.admin.dict.dto.DictDataVO
import controller.admin.dict.dto.DictTypeVO
import dto.PageResponse
import model.DictData
import model.DictType
import table.DictDataTable
import table.DictTypeTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.logging.Logger
import infra.SimpleCache
import neton.database.dsl.*


class DictLogic(
    private val log: Logger,
    private val cache: SimpleCache
) {

    companion object {
        private const val DICT_CACHE_PREFIX = "system:dict:data:"
    }

    // --- DictType CRUD ---

    suspend fun pageDictTypes(
        page: Int,
        size: Int,
        keyword: String? = null,
        name: String? = null,
        type: String? = null,
        status: Int? = null
    ): PageResponse<DictTypeVO> {
        val result = DictTypeTable.query {
            where {
                and(
                    whenNotBlank(keyword) {
                        or(
                            DictType::name like "%$it%",
                            DictType::type like "%$it%"
                        )
                    },
                    whenNotBlank(name) { DictType::name like "%$it%" },
                    whenNotBlank(type) { DictType::type like "%$it%" },
                    whenPresent(status) { DictType::status eq it }
                )
            }
            orderBy(DictType::id.desc())
        }.page(page, size)

        val items = result.items.map { it.toVO() }
        return PageResponse(
            list = items,
            total = result.total,
            page = page,
            size = size,
            totalPages = ((result.total + size - 1) / size).toInt()
        )
    }

    suspend fun getDictTypeById(id: Long): DictTypeVO {
        val dictType = DictTypeTable.get(id)
            ?: throw NotFoundException("Dict type not found")
        return dictType.toVO()
    }

    suspend fun createDictType(dictType: DictType): Long {
        val existing = DictTypeTable.oneWhere {
            DictType::type eq dictType.type
        }

        if (existing != null) {
            throw BadRequestException("Dict type already exists")
        }

        return DictTypeTable.insert(dictType).id
    }

    suspend fun updateDictType(dictType: DictType) {
        val existing = DictTypeTable.get(dictType.id)
            ?: throw NotFoundException("Dict type not found")

        // dictType is the stable foreign key used by all dictionary values and caches.
        if (existing.type != dictType.type) {
            throw BadRequestException("Dict type identifier cannot be changed")
        }

        DictTypeTable.update(dictType)

        // Invalidate cache for this dict type
        cache.delete("$DICT_CACHE_PREFIX${dictType.type}")
    }

    suspend fun deleteDictType(id: Long) {
        val dictType = DictTypeTable.get(id)
            ?: throw NotFoundException("Dict type not found")

        // Check if dict data exists
        val dataCount = DictDataTable.query {
            where { DictData::dictType eq dictType.type }
        }.list()

        if (dataCount.isNotEmpty()) {
            throw BadRequestException("Cannot delete dict type with associated data")
        }

        DictTypeTable.destroy(id)
        cache.delete("$DICT_CACHE_PREFIX${dictType.type}")
    }

    suspend fun listAllDictTypesSimple(): List<DictTypeVO> {
        return DictTypeTable.query {
            where { DictType::status eq 1 }
            orderBy(DictType::id.asc())
        }.list().map { it.toVO() }
    }

    // --- DictData CRUD ---

    suspend fun pageDictData(
        page: Int,
        size: Int,
        dictType: String? = null,
        label: String? = null,
        status: Int? = null
    ): PageResponse<DictDataVO> {
        val result = DictDataTable.query {
            where {
                and(
                    whenNotBlank(dictType) { DictData::dictType eq it },
                    whenNotBlank(label) { DictData::label like "%$it%" },
                    whenPresent(status) { DictData::status eq it }
                )
            }
            orderBy(DictData::sort.asc())
        }.page(page, size)

        val items = result.items.map { it.toVO() }
        return PageResponse(
            list = items,
            total = result.total,
            page = page,
            size = size,
            totalPages = ((result.total + size - 1) / size).toInt()
        )
    }

    suspend fun getDictDataById(id: Long): DictDataVO {
        val dictData = DictDataTable.get(id)
            ?: throw NotFoundException("Dict data not found")
        return dictData.toVO()
    }

    suspend fun createDictData(dictData: DictData): Long {
        val inserted = DictDataTable.insert(dictData)
        cache.delete("$DICT_CACHE_PREFIX${dictData.dictType}")
        return inserted.id
    }

    suspend fun updateDictData(dictData: DictData) {
        val existing = DictDataTable.get(dictData.id)
            ?: throw NotFoundException("Dict data not found")

        DictDataTable.update(dictData)

        // Invalidate cache for both old and new dict type
        cache.delete("$DICT_CACHE_PREFIX${existing.dictType}")
        if (existing.dictType != dictData.dictType) {
            cache.delete("$DICT_CACHE_PREFIX${dictData.dictType}")
        }
    }

    suspend fun deleteDictData(id: Long) {
        val dictData = DictDataTable.get(id)
            ?: throw NotFoundException("Dict data not found")

        DictDataTable.destroy(id)
        cache.delete("$DICT_CACHE_PREFIX${dictData.dictType}")
    }

    suspend fun listAllSimpleDictData(): List<DictDataVO> {
        val dataList = DictDataTable.query {
            where { DictData::status eq 1 }
            orderBy(DictData::sort.asc())
        }.list()
        return dataList.map { it.toVO() }
    }

    suspend fun getDataByType(type: String): List<DictDataVO> {
        // Try cache first
        val cached = cache.getList<DictDataVO>("$DICT_CACHE_PREFIX$type")
        if (cached != null) {
            return cached
        }

        val dataList = DictDataTable.query {
            where {
                and(
                    DictData::dictType eq type,
                    DictData::status eq 1
                )
            }
            orderBy(DictData::sort.asc())
        }.list().map { it.toVO() }

        // Store in cache
        cache.setList("$DICT_CACHE_PREFIX$type", dataList)

        return dataList
    }

    private fun DictType.toVO() = DictTypeVO(
        id = id,
        name = name,
        type = type,
        status = status,
        remark = remark
    )

    private fun DictData.toVO() = DictDataVO(
        id = id,
        dictType = dictType,
        label = label,
        value = value,
        sort = sort,
        status = status,
        remark = remark
    )
}
