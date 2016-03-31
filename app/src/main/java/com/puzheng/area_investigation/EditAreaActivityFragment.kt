package com.puzheng.area_investigation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.*
import com.orhanobut.logger.Logger
import com.puzheng.area_investigation.model.Area
import com.puzheng.area_investigation.store.AreaStore
import kotlinx.android.synthetic.main.fragment_create_area_step2.*
import rx.android.schedulers.AndroidSchedulers

/**
 * A placeholder fragment containing a simple view.
 */
class EditAreaActivityFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?) =
            inflater!!.inflate(R.layout.fragment_edit_area, container, false)

    // 这里使用常量，避免修改初始区域数据
    lateinit private var originArea: Area

    lateinit private var hotCopyArea: Area // 用于保存编辑状态下的区域信息


    private val markerBitmap: Bitmap by lazy {
        Bitmap.createScaledBitmap(
                activity.loadBitmap(R.drawable.marker),
                (8 * pixelsPerDp).toInt(),
                (8 * pixelsPerDp).toInt(),
                false
        )
    }

    private val markerEditModeBitmap: Bitmap by lazy {
        Bitmap.createScaledBitmap(
                activity.loadBitmap(R.drawable.marker_edit_mode),
                (32 * pixelsPerDp).toInt(),
                (32 * pixelsPerDp).toInt(),
                false
        )
    }

    private val touchingMarkerBitmap: Bitmap by lazy {
        Bitmap.createScaledBitmap(
                activity.loadBitmap(R.drawable.touching_marker),
                (64 * pixelsPerDp).toInt(),
                (64 * pixelsPerDp).toInt(),
                false
        )
    }

    private val selectedMarkerBitmap: Bitmap by lazy {
        Bitmap.createScaledBitmap(
                activity.loadBitmap(R.drawable.selected_marker),
                (32 * pixelsPerDp).toInt(),
                (32 * pixelsPerDp).toInt(),
                false
        )
    }

    private var markers: List<Marker>? = null

    private var polylines: List<Polyline>? = null

    private var selectedMarker: Marker? = null

    private var polygon: Polygon? = null

    private val POLYLINE_CLOSE_LIMIT: Double by lazy { 8 * pixelsPerDp }

    var editMode: Boolean = false
        set(b) {
            field = b
            if (b) {
                markers?.forEach {
                    it.setIcon(BitmapDescriptorFactory.fromBitmap(markerEditModeBitmap))
                    it.isDraggable = true
                }
                hotCopyArea = originArea.copy()
            } else {
                // 恢复为初始数据
                hotCopyArea = originArea.copy()
                map.map.setupOverlays()
            }
        }



    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        map.onCreate(savedInstanceState)
        originArea = activity.intent.getParcelableExtra<Area>(AreaListActivity.TAG_AREA)
        // 必须在地图加载完毕之后才可以缩放，否则会产生缩放不正确的情况
        map.map.setOnMapLoadedListener {
            map.map.apply {
                moveCamera(
                        CameraUpdateFactory.newLatLngBounds(
                                LatLngBounds.Builder().apply {
                                    originArea.outline.forEach { include(it) }
                                }.build(), (8 * pixelsPerDp).toInt()))
                setupOverlays()
                setOnMapLongClickListener {
                    listener?.onMapLongClick()
                    if (editMode) {
                        val polyline = findPolylineCloseTo(it)
                        if (polyline != null) {
                            val index = hotCopyArea.outline.indexOf(polyline.points[0])
                            hotCopyArea.outline = hotCopyArea.outline.toMutableList().apply { add(index + 1, it) }
                            setupOverlays()
                        }
                    }
                }
            }
        }
    }

    // thanks to http://stackoverflow.com/questions/849211/shortest-distance-between-a-point-and-a-line-segment
    private fun nearestPoint2SegDistance(p: Point, seg: Pair<Point, Point>): Double {
        Logger.v(p.toString() + "-" + seg.toString())
        fun squaredDistance(p0: Point, p1: Point) = Math.pow((p0.x - p1.x).toDouble(), 2.0) +
                Math.pow((p0.y - p1.y).toDouble(), 2.0)
        fun distance(p0: Point, p1: Point) = Math.sqrt(squaredDistance(p0, p1))

        val l2 = squaredDistance(seg.first, seg.second)  // i.e. |w-v|^2 -  avoid a sqrt
        if (l2 == 0.0) return distance(p, seg.first)   // v == w case
        // Consider the line extending the segment, parameterized as v + t (w - v).
        // We find projection of point p onto the line.
        // It falls where t = [(p-v) . (w-v)] / |w-v|^2
        // We clamp t from [0,1] to handle points outside the segment vw.
        var t = ((p.x - seg.first.x) * (seg.second.x - seg.first.x) + (p.y - seg.first.y) * (seg.second.y - seg.first.y)) / l2
        t = Math.max(0.0, Math.min(1.0, t))
        return distance(p, Point(seg.first.x + (t * (seg.second.x - seg.first.x)).toInt(),
                seg.first.y + (t * (seg.second.y - seg.first.y)).toInt()))
    }

    private val LatLng.screenLocation: Point
        get() = map.map.projection.toScreenLocation(this)

    private fun findPolylineCloseTo(latLng: LatLng) = polylines!!.map {
        nearestPoint2SegDistance(latLng.screenLocation, Pair(it.points[0].screenLocation, it.points[1].screenLocation)) to it
    }.filter {
        Logger.v(it.toString())
        it.first < POLYLINE_CLOSE_LIMIT
    }.minBy {
        it.first
    }?.second

    private var listener: OnFragmentInteractionListener? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context as OnFragmentInteractionListener?
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    interface OnFragmentInteractionListener {
        fun onMapLongClick()
        fun onMarkerSelected(position: LatLng)
    }

    fun deleteVertex(vertex: LatLng) {
        if (!editMode) {
            return
        }
        if (hotCopyArea.outline.size <= 3) {
            activity.toast(R.string.outline_needs_3_points)
            return
        }
        hotCopyArea.outline = hotCopyArea.outline.filter {
            it != vertex
        }
        map.map.setupOverlays()
    }

    // MR extensions
    private fun AMap.addPolygon(outline: Array<LatLng>) =
            addPolygon(PolygonOptions()
                    .add(*outline)
                    .fillColor(ContextCompat.getColor(activity, R.color.colorAccentLighter))
                    .strokeColor(ContextCompat.getColor(activity, android.R.color.transparent)))

    private fun AMap.addMarker(latLng: LatLng) =
            addMarker(MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromBitmap(if (editMode) markerEditModeBitmap else markerBitmap))
                    .position(latLng)
                    .anchor(0.5F, 0.5F).draggable(editMode))

    private var Marker.selected: Boolean
        get() = selectedMarker?.id == id
        set(b: Boolean) {
            markers?.forEach {
                it.setIcon(
                        BitmapDescriptorFactory.fromBitmap(markerEditModeBitmap)
                )
            }
            if (b) {
                selectedMarker = this
                setIcon(BitmapDescriptorFactory.fromBitmap(selectedMarkerBitmap))
            }
        }

    private fun AMap.setupOverlays() {
        val area = if (editMode) hotCopyArea else originArea
        polygon?.remove()
        polygon = addPolygon(area.outline.toTypedArray())
        markers?.forEach { it.remove() }
        markers = area.outline.map {
            addMarker(it)
        }
        polylines?.forEach { it.remove() }
        polylines = area.outline.withIndex().map {
            val (idx, latLng) = it
            addPolyline(PolylineOptions().add(area.outline[(idx - 1 + area.outline.size) % area.outline.size],
                    latLng).width((2 * pixelsPerDp).toFloat())
                    .color(ContextCompat.getColor(activity, R.color.colorAccent)))
        }
        setOnMarkerDragListener(object : AMap.OnMarkerDragListener {

            override fun onMarkerDragEnd(p0: Marker?) {
                p0?.setIcon(BitmapDescriptorFactory.fromBitmap(markerEditModeBitmap))
                hotCopyArea.outline = markers!!.map { it.position }
                setupOverlays()
            }

            override fun onMarkerDragStart(p0: Marker?) {
                p0?.setIcon(BitmapDescriptorFactory.fromBitmap(touchingMarkerBitmap))
            }

            override fun onMarkerDrag(p0: Marker?) {

            }
        })
        setOnMarkerClickListener {
            if (editMode) {
                it.selected = true
                listener?.onMarkerSelected(it.position)
            }
            true
        }
    }

    fun saveOutline(afterSaving: () -> Unit) {
        ConfirmSaveAreaOutlineDialog(hotCopyArea, {
            originArea = hotCopyArea
            afterSaving()
        }).show(activity.supportFragmentManager, "")
    }

}
