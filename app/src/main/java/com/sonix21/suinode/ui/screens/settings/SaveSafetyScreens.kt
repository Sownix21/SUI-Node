package com.sonix21.suinode.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.core.*
import com.sonix21.suinode.data.*
import com.sonix21.suinode.ui.glass.*
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SaveReviewHost() {
    val request by SaveGuard.review.collectAsState()
    val r = request ?: return
    ModalBottomSheet(onDismissRequest = { r.decision.complete(false) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Review panel changes", style=MaterialTheme.typography.titleLarge)
            Text(r.title)
            Text("Nothing has been sent yet. Saving may restart the VPN core and interrupt connections. Secrets and unrecognized text are hidden. APIv2 has no atomic version lock; avoid concurrent edits in other apps.")
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(r.lines.size) { Text(r.lines[it],style=MaterialTheme.typography.bodySmall) }
                if(r.lines.isEmpty()) item { Text("No field differences detected. Review whether this operation is necessary.") }
            }
            Button(onClick={r.decision.complete(true)},modifier=Modifier.fillMaxWidth()) { Text("Send reviewed changes") }
            OutlinedButton(onClick={r.decision.complete(false)},modifier=Modifier.fillMaxWidth()) { Text("Cancel · keep draft") }
        }
    }
}

@Composable
fun PendingSaveScreen(nav: NavController,panelId: String) {
    val context=LocalContext.current
    val store=remember { PanelStore(context.applicationContext) }
    val runner=rememberRunner()
    var pending by remember { mutableStateOf<JSONObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var acknowledge by remember { mutableStateOf(false) }
    val panel=Panels.list.collectAsState().value.firstOrNull { it.id==panelId }
    fun load()=runner.go { pending=withContext(Dispatchers.IO){store.localRecord("pendingSaves",panelId)}; loaded=true }
    LaunchedEffect(panelId){load()}
    if(acknowledge) ConfirmDialog("Allow a new attempt?","Only continue after inspecting the actual panel state. A timeout may have applied the previous change. This removes the local retry block but sends nothing and does not undo anything.",confirmText="I reviewed the panel",danger=true,
        onConfirm={runner.go{withContext(Dispatchers.IO){store.saveLocalRecord("pendingSaves",panelId,null)};pending=null;result="Local retry block removed. Any new save still requires your action."}},onDismiss={acknowledge=false})
    PageScaffold("Pending changes",nav,subtitle=panel?.name,busy=runner.busy){
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){
            item { runner.error?.let{Text(it,color=LocalGlass.current.err)}; Text(result,color=LocalGlass.current.textFaint) }
            if(pending==null) item { Text(if(loaded) "No unresolved save recorded for this panel." else "Reading encrypted save receipt…",color=LocalGlass.current.text) }
            else {
                val p=requireNotNull(pending)
                item { Text("${p.optString("action")} ${p.optString("object")} · ${java.time.Instant.ofEpochSecond(p.optLong("time"))}",color=LocalGlass.current.text)
                    Text("This is an uncertain outcome, not proof of failure. The app will not automatically retry the write.",color=LocalGlass.current.textFaint) }
                val lines=p.optJSONArray("summary")?.strList().orEmpty()
                items(lines.size){Text(lines[it],color=LocalGlass.current.textFaint)}
                item { PrimaryButton("Check actual state (GET only)",enabled=panel!=null&&!runner.busy){runner.go{
                    val api=SuiClient(requireNotNull(panel))
                    try {
                        val matches=SaveEvidence.matches(p,SaveGuard.current(api,p))
                        if(matches){withContext(Dispatchers.IO){store.saveLocalRecord("pendingSaves",panelId,null)};pending=null;result="GET confirms the requested state. No retry was sent."}
                        else result="GET does not confirm the entire requested state. It may be unapplied, partially applied, normalized by the server, or changed again. Inspect before retrying."
                    }finally{api.logout()}
                }} }
                item { GhostButton("Acknowledge manual review",enabled=!runner.busy){acknowledge=true} }
            }
        }
    }
}
