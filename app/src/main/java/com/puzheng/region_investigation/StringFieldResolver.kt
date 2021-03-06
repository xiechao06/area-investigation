package com.puzheng.region_investigation

import android.content.Context
import android.support.design.widget.TextInputLayout
import android.view.View
import android.widget.EditText
import com.orhanobut.logger.Logger
import com.puzheng.region_investigation.model.POI
import org.json.JSONObject


open class StringFieldResolver(override val name: String, val context: Context) : FieldResolver {

    override fun changed(value: Any?) = editText.text.toString().trim() != value?.toString()?.trim() ?: ""


    open protected val layoutId = R.layout.poi_field_string

    private val editText: EditText by lazy {
        view.findViewById(R.id.editText) as EditText
    }

    override fun populate(jsonObject: JSONObject, poi: POI) {
        jsonObject.put(name, editText.text.toString())
    }

    private val view: View by lazy {
        View.inflate(context, layoutId, null).apply {
            (this as TextInputLayout).hint = "请输入$name"
        }
    }

    private var text: String? = null

    override fun bind(value: Any?): View {
        val text = try {
            value as String?
        } catch (e: ClassCastException) {
            ""
        }
        editText.setText(text)
        return view
    }
}

