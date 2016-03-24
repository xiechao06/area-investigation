package com.puzheng.area_investigation

import android.content.Context
import android.support.v4.view.ViewCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.orhanobut.logger.Logger

import com.puzheng.area_investigation.AreaListFragment.OnAreaListFragmentInteractionListener
import com.puzheng.area_investigation.dummy.DummyContent.DummyItem
import com.puzheng.area_investigation.model.Area
import com.puzheng.area_investigation.store.AreaStore
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_area.view.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val HEADER_TYPE = 1
private val AREA_TYPE = 2

/**
 * [RecyclerView.Adapter] that can display a [DummyItem] and makes a call to the
 * specified [OnAreaListFragmentInteractionListener].
 * TODO: Replace the implementation with code for your data type.
 */
class AreaRecyclerViewAdapter(private val areas: List<Area?>?,
                              private val listener: OnAreaListFragmentInteractionListener) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {


    val items = mutableListOf<Area?>()

    init {
        if (areas != null) {
            for ((idx, area) in areas.withIndex()) {
                // 按天分组，如果不是同一天的，插入null，代表一个seperator
                if (idx == 0 || !area!!.created.ofSameDay(areas[idx - 1]!!.created)) {
                    items.add(null)
                }
                items.add(area)
            }
        }

    }

    override fun getItemViewType(position: Int): Int = if (items[position] == null) {
        HEADER_TYPE
    } else {
        AREA_TYPE
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        fun inflate(layout: Int) = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return if (viewType == HEADER_TYPE) {
            HeaderViewHolder(inflate(R.layout.fragment_area_header))
        } else {
            AreaViewHolder(inflate(R.layout.fragment_area))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val area = items[position]
        if (area == null) {
            val format = SimpleDateFormat("yy-MM-dd")
            (holder as HeaderViewHolder).textView.text = format.format(items[position + 1]!!.created)
        } else {
            (holder as AreaViewHolder).item = items[position]
            holder.textView.text = area.name
            val context = holder.textView.context
            Picasso.with(context).load(AreaStore.with(context).getCoverImageFile(area)).into(holder.imageView);
            Logger.v("bind ${area.name}")
            holder.view.setOnClickListener {
                listener.onClickItem(holder.item!!)
            }
            holder.view.setOnLongClickListener {
                listener.onLongClickItem(holder.item!!)
                true
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class LayoutManager(context: Context, spanSize: Int) : GridLayoutManager(context, spanSize) {
        init {
            this.spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = if (items[position] == null) {
                    2
                } else {
                    1
                }
            }
        }
    }

}

private class AreaViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
    val textView: TextView
    val imageView: ImageView
    var item: Area? = null

    init {
        textView = view.findViewById(R.id.content) as TextView
        imageView = view.findViewById(R.id.imageView) as ImageView
    }

    override fun toString(): String {
        return super.toString() + " '" + textView.text + "'"
    }
}

private class HeaderViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
    val textView: TextView

    init {
        textView = view.findViewById(R.id.text) as TextView
    }
}