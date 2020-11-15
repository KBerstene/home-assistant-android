package io.homeassistant.companion.android.widgets.common

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.maltaisn.icondialog.IconDialog
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.Service
import io.homeassistant.companion.android.widgets.multi.MultiWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetButton
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElement
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetPlaintext
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetTemplate
import kotlinx.android.synthetic.main.widget_multi_config_button.view.*
import kotlinx.android.synthetic.main.widget_multi_config_plaintext.view.*
import kotlinx.android.synthetic.main.widget_multi_config_template.view.*

class WidgetDynamicElementAdapter(
    private var context: MultiWidgetConfigureActivity,
    private var elements: ArrayList<MultiWidgetElement>,
    private var entities: HashMap<String, Entity<Any>>,
    private var services: HashMap<String, Service>,
    private var serviceAdapter: SingleItemArrayAdapter<Service>,
    private var entityFilterCheckbox: AppCompatCheckBox,
    private var entityIdTextView: AutoCompleteTextView
) : RecyclerView.Adapter<WidgetDynamicElementAdapter.ViewHolder>() {
    companion object {
        private const val TAG = "MultiWidgetButtonElem"
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    lateinit var iconDialog: IconDialog
    lateinit var getTemplateTextAsync: (templateText: String, renderView: AppCompatTextView) -> Unit

    override fun getItemCount(): Int {
        return elements.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (elements[position].type) {
            MultiWidgetElement.Type.BUTTON -> R.layout.widget_multi_config_button
            MultiWidgetElement.Type.PLAINTEXT -> R.layout.widget_multi_config_plaintext
            MultiWidgetElement.Type.TEMPLATE -> R.layout.widget_multi_config_template
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val dynamicElementLayout = inflater.inflate(viewType, parent, false)

        return ViewHolder(dynamicElementLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Bind type-agnostic views
        val elementView = holder.itemView

        // Store layout views so the element can finalize its own values
        elements[position].layout = elementView

        // Set up remove element button
        elementView.findViewById<AppCompatImageButton>(
            R.id.widget_element_remove_button
        ).setOnClickListener {
            elements.removeAt(position)
            notifyItemRemoved(position)
        }

        // Bind type-specific views
        when (elements[position].type) {
            MultiWidgetElement.Type.BUTTON -> bindButtonViews(
                elementView,
                elements[position] as MultiWidgetButton
            )
            MultiWidgetElement.Type.PLAINTEXT -> bindPlaintextViews(
                elementView,
                elements[position] as MultiWidgetPlaintext
            )
            MultiWidgetElement.Type.TEMPLATE -> bindTemplateViews(
                elementView,
                elements[position] as MultiWidgetTemplate
            )
        }
    }

    internal fun addButton(iconDialog: IconDialog) {
        this.iconDialog = iconDialog
        elements.add(MultiWidgetButton(services))
        notifyItemInserted(elements.size - 1)
    }

    internal fun addTemplate(getTemplateTextAsync: (templateText: String, renderView: AppCompatTextView) -> Unit) {
        this.getTemplateTextAsync = getTemplateTextAsync
        elements.add(MultiWidgetTemplate())
        notifyItemInserted(elements.size - 1)
    }

    internal fun addPlaintext() {
        elements.add(MultiWidgetPlaintext())
        notifyItemInserted(elements.size - 1)
    }

    private fun bindButtonViews(dynamicElementLayout: View, element: MultiWidgetButton) {
        // Prepare dynamic field variables
        val dynamicFields = ArrayList<ServiceFieldBinder>()
        val dynamicFieldAdapter = WidgetDynamicFieldAdapter(services, entities, dynamicFields)

        // Store dynamic fields for later processing
        element.dynamicFields = dynamicFields

        // Set up service edit text field
        dynamicElementLayout.widget_element_service_text.setAdapter(serviceAdapter)
        dynamicElementLayout.widget_element_service_text.addTextChangedListener(
            createServiceTextWatcher(
                dynamicFields,
                dynamicFieldAdapter
            )
        )
        dynamicElementLayout.widget_element_service_text.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus && view is AutoCompleteTextView) {
                view.showDropDown()
            }
        }

        // Set up dynamic field layout
        dynamicElementLayout.widget_element_fields_layout.adapter = dynamicFieldAdapter
        dynamicElementLayout.widget_element_fields_layout.layoutManager =
            LinearLayoutManager(context)

        // Set up add field button
        dynamicElementLayout.widget_element_add_field_button.setOnClickListener(
            createAddFieldListener(
                dynamicFields,
                dynamicFieldAdapter,
                dynamicElementLayout.widget_element_service_text
            )
        )

        // Set up icon selection button
        dynamicElementLayout.widget_element_icon_selector.setOnClickListener {
            iconDialog.show(context.supportFragmentManager, element.tag)
        }
    }

    private fun bindPlaintextViews(dynamicElementLayout: View, element: MultiWidgetPlaintext) {
        // Set up the text size spinner
        dynamicElementLayout.widget_element_label_text_size.adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.widget_label_font_size,
                android.R.layout.simple_spinner_item
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
    }

    private fun bindTemplateViews(dynamicElementLayout: View, element: MultiWidgetTemplate) {
        // Have the user-edited template text get passed back to the main activity so it can
        // render in a coroutine and the rendered text can be updated asynchronously
        val templateTextEdit = dynamicElementLayout.widget_element_template_edit
        val templateTextRender = dynamicElementLayout.widget_element_template_render
        templateTextEdit.doAfterTextChanged {
            getTemplateTextAsync(
                templateTextEdit.text.toString(),
                templateTextRender
            )
        }

        // Set up the text size spinner
        dynamicElementLayout.widget_element_template_text_size.adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.widget_label_font_size,
                android.R.layout.simple_spinner_item
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
    }

    private fun createServiceTextWatcher(
        dynamicFields: ArrayList<ServiceFieldBinder>,
        dynamicFieldAdapter: WidgetDynamicFieldAdapter
    ): TextWatcher {
        return (object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val serviceText: String = p0.toString()

                if (services.keys.contains(serviceText)) {
                    Log.d(
                        TAG,
                        "Valid domain and service--processing dynamic fields"
                    )

                    // Make sure there are not already any dynamic fields created
                    // This can happen if selecting the drop-down twice or pasting
                    dynamicFields.clear()

                    // We only call this if servicesAvailable was fetched and is not null,
                    // so we can safely assume that it is not null here
                    val fields = services[serviceText]!!.serviceData.fields
                    val fieldKeys = fields.keys
                    Log.d(
                        TAG,
                        "Fields applicable to this service: $fields"
                    )

                    fieldKeys.sorted().forEach { fieldKey ->
                        Log.d(
                            TAG,
                            "Creating a text input box for $fieldKey"
                        )

                        // Insert a dynamic layout
                        // IDs get priority and go at the top, since the other fields
                        // are usually optional but the ID is required
                        if (fieldKey.contains("_id")) {
                            dynamicFields.add(
                                0, ServiceFieldBinder(
                                    serviceText,
                                    fieldKey,
                                    if (entityFilterCheckbox.isChecked) entityIdTextView.text.toString() else null
                                )
                            )
                        } else
                            dynamicFields.add(
                                ServiceFieldBinder(
                                    serviceText,
                                    fieldKey
                                )
                            )
                    }

                    dynamicFieldAdapter.notifyDataSetChanged()
                } else {
                    if (dynamicFields.size > 0) {
                        dynamicFields.clear()
                        dynamicFieldAdapter.notifyDataSetChanged()
                    }
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
    }

    private fun createAddFieldListener(
        dynamicFields: ArrayList<ServiceFieldBinder>,
        dynamicFieldAdapter: WidgetDynamicFieldAdapter,
        serviceTextView: AutoCompleteTextView
    ): View.OnClickListener {
        return View.OnClickListener {
            val fieldKeyInput = EditText(context)
            fieldKeyInput.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            AlertDialog.Builder(context)
                .setTitle("Field")
                .setView(fieldKeyInput)
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    dynamicFields.add(
                        ServiceFieldBinder(
                            serviceTextView.text.toString(),
                            fieldKeyInput.text.toString()
                        )
                    )

                    dynamicFieldAdapter.notifyDataSetChanged()
                }
                .show()
        }
    }
}
