package controller.admin.dict

import controller.admin.dict.dto.CreateDictDataRequest
import controller.admin.dict.dto.DictDataVO
import controller.admin.dict.dto.UpdateDictDataRequest
import dto.PageResponse
import logic.DictLogic
import model.DictData
import neton.core.annotations.*

@Controller("/system/dict-data")
class DictDataController(
    private val dictLogic: DictLogic
) {

    @Get("/page")
    @Permission("system:dict:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query dictType: String? = null,
        @Query label: String? = null,
        @Query status: Int? = null
    ): PageResponse<DictDataVO> {
        return dictLogic.pageDictData(pageNo, pageSize, dictType, label, status)
    }

    @Get("/simple-list")
    @AllowAnonymous
    suspend fun simpleList(): List<DictDataVO> {
        return dictLogic.listAllSimpleDictData()
    }

    @Get("/get/{id}")
    @Permission("system:dict:query")
    suspend fun get(@PathVariable id: Long): DictDataVO {
        return dictLogic.getDictDataById(id)
    }

    @Get("/type")
    @Permission("system:dict:query")
    suspend fun getByType(@Query type: String): List<DictDataVO> {
        return dictLogic.getDataByType(type)
    }

    @Post("/create")
    @Permission("system:dict:create")
    suspend fun create(@Body request: CreateDictDataRequest): Long {
        return dictLogic.createDictData(
            DictData(
                dictType = request.dictType,
                label = request.label,
                value = request.value,
                sort = request.sort,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Put("/update")
    @Permission("system:dict:update")
    suspend fun update(@Body request: UpdateDictDataRequest) {
        dictLogic.updateDictData(
            DictData(
                id = request.id,
                dictType = request.dictType,
                label = request.label,
                value = request.value,
                sort = request.sort,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Delete("/delete/{id}")
    @Permission("system:dict:delete")
    suspend fun delete(@PathVariable id: Long) {
        dictLogic.deleteDictData(id)
    }

    @Delete("/delete-list")
    @Permission("system:dict:delete")
    suspend fun deleteList(@Query ids: String) {
        ids.split(",").mapNotNull { it.trim().toLongOrNull() }.forEach { dictLogic.deleteDictData(it) }
    }
}
