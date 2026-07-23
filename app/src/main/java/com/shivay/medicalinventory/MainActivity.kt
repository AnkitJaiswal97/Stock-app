package com.shivay.medicalinventory
import androidx.compose.material3.ExperimentalMaterial3Api
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Medicine(
    val id: Long = System.currentTimeMillis(),
    val brand: String, val composition: String, val company: String,
    val batch: String, val mrp: String, val expiry: String,
    val quantity: Int, val rack: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFC62828))) {
            InventoryApp(this)
        }}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryApp(context: Context) {
    var medicines by remember { mutableStateOf(load(context)) }
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }

    fun persist(list: List<Medicine>) { medicines = list; save(context, list) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Shivay Medical Inventory") }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("+") } }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            OutlinedTextField(search, { search = it }, label = { Text("Search A–Z medicines") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All","Low","Expiry").forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
                }
            }
            val shown = medicines.filter {
                val matches = it.brand.contains(search,true) || it.composition.contains(search,true) || it.company.contains(search,true)
                matches && when(filter) {
                    "Low" -> it.quantity <= 5
                    "Expiry" -> isSoonOrExpired(it.expiry)
                    else -> true
                }
            }.sortedBy { it.brand.lowercase() }

            Text("${shown.size} medicines", modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn {
                items(shown, key={it.id}) { m ->
                    Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(m.brand, style=MaterialTheme.typography.titleMedium)
                            Text("${m.composition} • ${m.company}")
                            Text("Batch: ${m.batch}   MRP: ₹${m.mrp}")
                            Text("Expiry: ${m.expiry}   Rack: ${m.rack}")
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Button(onClick={ persist(medicines.map { if(it.id==m.id) it.copy(quantity=(it.quantity-1).coerceAtLeast(0)) else it }) }) { Text("−") }
                                Text("Stock: ${m.quantity}", modifier=Modifier.padding(top=12.dp))
                                Button(onClick={ persist(medicines.map { if(it.id==m.id) it.copy(quantity=it.quantity+1) else it }) }) { Text("+") }
                                TextButton(onClick={ persist(medicines.filterNot { it.id==m.id }) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
    if(showAdd) AddDialog(onDismiss={showAdd=false}) { m -> persist(medicines + m); showAdd=false }
}

@Composable
fun AddDialog(onDismiss:()->Unit, onAdd:(Medicine)->Unit) {
    var brand by remember { mutableStateOf("") }; var comp by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }; var batch by remember { mutableStateOf("") }
    var mrp by remember { mutableStateOf("") }; var expiry by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }; var rack by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("Add Medicine")},
        text={ LazyColumn {
            item { Field("Brand name",brand){brand=it}; Field("Composition",comp){comp=it}; Field("Company",company){company=it}
                Field("Batch number",batch){batch=it}; Field("MRP",mrp){mrp=it}; Field("Expiry (MM/YYYY)",expiry){expiry=it}
                Field("Quantity",qty){qty=it}; Field("Rack location",rack){rack=it} }
        }},
        confirmButton={Button(enabled=brand.isNotBlank(), onClick={onAdd(Medicine(brand=brand.trim(),composition=comp.trim(),company=company.trim(),batch=batch.trim(),mrp=mrp.trim(),expiry=expiry.trim(),quantity=qty.toIntOrNull()?:0,rack=rack.trim()))}){Text("Save")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable fun Field(label:String, value:String, change:(String)->Unit) {
    OutlinedTextField(value,change,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=3.dp))
}
fun save(c:Context, list:List<Medicine>) {
    val a=JSONArray(); list.forEach { m->a.put(JSONObject().apply {
        put("id",m.id);put("brand",m.brand);put("composition",m.composition);put("company",m.company);put("batch",m.batch);put("mrp",m.mrp);put("expiry",m.expiry);put("quantity",m.quantity);put("rack",m.rack)
    })}; c.getSharedPreferences("inventory",Context.MODE_PRIVATE).edit().putString("data",a.toString()).apply()
}
fun load(c:Context):List<Medicine> = try {
    val a=JSONArray(c.getSharedPreferences("inventory",Context.MODE_PRIVATE).getString("data","[]"))
    (0 until a.length()).map { i->a.getJSONObject(i).let { Medicine(it.getLong("id"),it.getString("brand"),it.getString("composition"),it.getString("company"),it.getString("batch"),it.getString("mrp"),it.getString("expiry"),it.getInt("quantity"),it.getString("rack")) } }
} catch(e:Exception){ emptyList() }
fun isSoonOrExpired(expiry:String):Boolean = try {
    val f=SimpleDateFormat("MM/yyyy",Locale.US); f.isLenient=false
    val d=f.parse(expiry)?:return false
    val cal=Calendar.getInstance(); cal.add(Calendar.MONTH,3)
    d.before(cal.time)
} catch(e:Exception){false}
