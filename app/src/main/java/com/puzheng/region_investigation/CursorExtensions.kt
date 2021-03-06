package com.puzheng.region_investigation

import android.database.Cursor
import android.provider.BaseColumns
import com.amap.api.maps.model.LatLng
import com.puzheng.region_investigation.model.Region
import com.puzheng.region_investigation.model.POI
import org.json.JSONObject
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

fun Cursor.getDate(colName: String, format: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")): Date? {
    val index = getColumnIndexOrThrow(colName)
    return if (isNull(index)) null else {
        val s = getString(colName)
        format.parse(s)
    }
}

fun Cursor.getRegionRow() = Region(getLong(BaseColumns._ID)!!, getString(Region.Model.COL_NAME)!!,
        {
            it: String? ->
            val m = mutableMapOf<String, String>()
            if (it != null) {
                val jo = JSONObject(it)
                for (key in jo.keys()) {
                    m.put(key, jo.getString(key))
                }
            }
            m
        }(getString(Region.Model.COL_EXTRAS)),
        Region.decodeOutline(getString(Region.Model.COL_OUTLINE)!!),
        getDate(Region.Model.COL_CREATED)!!, getDate(Region.Model.COL_UPDATED),
        getDate(Region.Model.COL_SYNCED))


fun Cursor.getPOIRow() = POI(getLong(BaseColumns._ID)!!, getString(POI.Model.COL_POI_TYPE_NAME)!!,
        getLong(POI.Model.COL_REGION_ID)!!,
        POI.decodeLatLng(getString(POI.Model.COL_LAT_LNG)!!),
        getDate(POI.Model.COL_CREATED)!!, getDate(Region.Model.COL_UPDATED))