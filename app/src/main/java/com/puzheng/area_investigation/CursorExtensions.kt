package com.puzheng.area_investigation

import android.database.Cursor
import android.provider.BaseColumns
import com.amap.api.maps.model.LatLng
import com.puzheng.area_investigation.model.Area
import com.puzheng.area_investigation.model.POI
import java.text.SimpleDateFormat
import java.util.*


fun Cursor.getString(colName: String): String? {
    val index = getColumnIndexOrThrow(colName)
    return if (isNull(index)) null else getString(index)
}

fun Cursor.getLong(colName: String): Long? {
    val index = getColumnIndexOrThrow(colName)
    return if (isNull(index)) null else getLong(index)
}

fun Cursor.getDate(colName: String): Date? {
    val index = getColumnIndexOrThrow(colName)
    return if (isNull(index)) null else {
        val s = getString(colName)
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s)
    }
}

fun Cursor.getAreaRow() = Area(getLong(BaseColumns._ID)!!, getString(Area.Model.COL_NAME)!!,
        Area.decodeOutline(getString(Area.Model.COL_OUTLINE)!!),
        getDate(Area.Model.COL_CREATED)!!, getDate(Area.Model.COL_UPDATED))


fun Cursor.getPOIRow() = POI(getLong(BaseColumns._ID)!!, getString(POI.Model.COL_POI_TYPE_UUID)!!,
        getLong(POI.Model.COL_AREA_ID)!!, POI.decodeLatLng(getString(POI.Model.COL_LAT_LNG)!!),
        getDate(POI.Model.COL_CREATED)!!, getDate(Area.Model.COL_UPDATED))