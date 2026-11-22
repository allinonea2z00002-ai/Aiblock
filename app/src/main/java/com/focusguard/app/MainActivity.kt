package com.focusguard.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.focusguard.app.data.BlockStore
import com.focusguard.app.data.LocationRule
import com.focusguard.app.service.LocationGeofenceManager
import com.focusguard.app.pureshield.PureShieldPage
import java.util.UUID
import kotlinx.coroutines.delay

private val Blue = Color(0xFF347FE5)
private val Pale = Color(0xFFF0F2F7)
private val Ink = Color(0xFF202538)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { FocusGuardApp(this) } }
}

@Composable fun FocusGuardApp(activity: ComponentActivity) {
    var tab by remember { mutableStateOf("Dashboard") }
    var page by remember { mutableStateOf("") }
    val store = remember { BlockStore(activity) }
    var refresh by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Pale, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Pale) {
            Column {
                if (page.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page = "" }) { Icon(Icons.Default.ArrowBack, null) }
                        Text(page, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    when (page) {
                        "All Apps" -> AppsPage(activity, store) { refresh++ }
                        "All Sites" -> SitesPage(store) { refresh++ }
                        "Manage Keywords" -> KeywordsPage(store) { refresh++ }
                        "Notification Blocking" -> NotificationBlockingPage(activity, store) { refresh++ }
                        "Study Timer" -> TimerPage()
                        "Blocking Schedule" -> SchedulePage()
                        "Location Blocking" -> LocationBlockingPage(activity, store) { refresh++ }
                        "Strict Mode" -> StrictPage(store) { refresh++ }
                        "Settings" -> SettingsPage(activity)
                        "PURESHIELD AI" -> PureShieldPage(activity)
                        "Choose Blocking Type" -> BlockingTypePage { page = it }
                        "Choose Blocking Condition" -> ConditionPage { page = it }
                        "Create New Profile" -> ProfilePage { page = it }
                        "Block Reels/Shorts" -> ReelsPage()
                        "Take a Break" -> BreakPage()
                        else -> GenericPage(page)
                    }
                } else {
                    when (tab) {
                        "Dashboard" -> Dashboard(store, { page = it }, refresh)
                        "Modes" -> ModesPage({ page = it })
                        "Analytics" -> AnalyticsPage(activity)
                        else -> AccountPage({ page = it })
                    }
                    NavigationBar(containerColor = Color.White) {
                        listOf("Dashboard", "Modes", "Analytics", "Account").forEach { name ->
                            NavigationBarItem(selected = tab == name, onClick = { tab = name }, icon = {
                                Icon(when(name){"Dashboard"->Icons.Default.Dashboard;"Modes"->Icons.Default.Lock;"Analytics"->Icons.Default.BarChart;else->Icons.Default.Person}, null)
                            }, label = { Text(name, fontSize = 10.sp) })
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Dashboard(store: BlockStore, open: (String)->Unit, refresh: Int) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF5B70E8), Color(0xFF46B5D9))), RoundedCornerShape(bottomStart=24.dp,bottomEnd=24.dp)).padding(20.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { Text("FOCUS GUARD", color=Color.White, fontWeight=FontWeight.Bold); Icon(Icons.Default.Notifications, null, tint=Color.White) }
                Spacer(Modifier.height(24.dp)); Text("Select Mode", color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold)
                Text("Choose how you want to stay focused", color=Color.White.copy(.9f), fontSize=13.sp)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            ModeCard("Normal Mode", "Make any changes and uninstall the app freely.", false) { open("All Apps") }
            ModeCard("Lock Mode", "Set a password to block phone settings and app changes.", true) { open("Strict Mode") }
            ModeCard("Strict Mode", "Set restrictions to make blocking harder to disable.", true) { open("Strict Mode") }
            Text("Quick Access", fontWeight=FontWeight.Bold, color=Ink, fontSize=18.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Quick("Apps", Icons.Default.Apps) { open("All Apps") }; Quick("Sites", Icons.Default.Language) { open("All Sites") }; Quick("Timer", Icons.Default.Timer) { open("Study Timer") } }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Quick("Keywords", Icons.Default.Key) { open("Manage Keywords") }; Quick("Schedule", Icons.Default.CalendarMonth) { open("Blocking Schedule") }; Quick("Reels", Icons.Default.Block) { open("Block Reels/Shorts") } }
        }
    }
}
@Composable private fun ModeCard(title:String, desc:String, locked:Boolean, onClick:()->Unit) {
    Card(onClick=onClick, colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
            Icon(if(locked) Icons.Default.Lock else Icons.Default.MusicNote, null, tint=Blue, modifier=Modifier.size(30.dp))
            Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text(title, fontWeight=FontWeight.Bold, color=Ink); Text(desc, fontSize=12.sp, color=Color.Gray) }
            if(locked) Icon(Icons.Default.Lock, null, tint=Color.LightGray)
        }
    }
}
@Composable private fun RowScope.Quick(label:String, icon: androidx.compose.ui.graphics.vector.ImageVector, click:()->Unit) {
    Card(onClick=click, modifier=Modifier.weight(1f), colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment=Alignment.CenterHorizontally) { Icon(icon,null,tint=Blue); Text(label,fontSize=11.sp,color=Ink) } }
}
@Composable private fun ModesPage(open:(String)->Unit) { Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) { Text("Modes",fontSize=24.sp,fontWeight=FontWeight.Bold); listOf("All Apps","All Sites","Manage Keywords","Notification Blocking","PURESHIELD AI","Create New Profile","Choose Blocking Type","Choose Blocking Condition","Study Timer","Blocking Schedule","Location Blocking","Strict Mode","Block Reels/Shorts","Take a Break").forEach { MenuRow(it){open(it)} } } }
@Composable private fun MenuRow(title:String, onClick:()->Unit) { Card(onClick=onClick, colors=CardDefaults.cardColors(containerColor=Color.White)) { Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f),fontWeight=FontWeight.Medium); Icon(Icons.Default.ChevronRight,null,tint=Color.Gray) } } }
@Composable private fun AppsPage(ctx:Context, store:BlockStore, changed:()->Unit) {
    val apps = remember { ctx.packageManager.getInstalledApplications(0).filter { ctx.packageManager.getLaunchIntentForPackage(it.packageName)!=null && it.packageName != ctx.packageName }.sortedBy { ctx.packageManager.getApplicationLabel(it).toString().lowercase() } }
    var query by remember { mutableStateOf("") }; var tick by remember { mutableIntStateOf(0) }
    var limitTarget by remember { mutableStateOf<android.content.pm.ApplicationInfo?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text("Search / Add App")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true)
        Text("App Name                                      Blocked / Usage Time",color=Color.Gray,fontSize=11.sp,modifier=Modifier.padding(vertical=10.dp))
        LazyColumn { items(apps.filter { ctx.packageManager.getApplicationLabel(it).toString().contains(query,true) }) { app ->
            val label=ctx.packageManager.getApplicationLabel(app).toString(); val checked=if(store.isWhitelistMode()) app.packageName in store.allowedApps() else store.blockedApps().contains(app.packageName)
            Row(Modifier.fillMaxWidth().clickable { if(store.isWhitelistMode()){if(checked) store.removeAllowedApp(app.packageName) else store.addAllowedApp(app.packageName)}else{if(checked) store.removeApp(app.packageName) else store.addApp(app.packageName)}; tick++; changed() }.padding(vertical=9.dp), verticalAlignment=Alignment.CenterVertically) {
                val icon = remember(app.packageName) { ctx.packageManager.getApplicationIcon(app) }
                androidx.compose.foundation.Image(icon.toBitmapSafe().asImageBitmap(), contentDescription=null, modifier=Modifier.size(34.dp))
                Column(Modifier.weight(1f).padding(start=12.dp)) { Text(label,fontWeight=FontWeight.Medium,color=Ink); Text(if(checked) { if(store.isWhitelistMode()) "Allowed" else "Blocked" } else { if(store.isWhitelistMode()) "Tap to allow" else "Tap to block" },fontSize=11.sp,color=Color.Gray) }
                IconButton(onClick={limitTarget=app}) { Icon(Icons.Default.MoreVert,null,tint=Color.Gray) }
                Checkbox(checked=checked,onCheckedChange={if(store.isWhitelistMode()){if(it)store.addAllowedApp(app.packageName) else store.removeAllowedApp(app.packageName)}else{if(it)store.addApp(app.packageName) else store.removeApp(app.packageName)};tick++;changed()})
            }; HorizontalDivider(color=Color(0xFFE9EAF0))
        } }
    }
    if (limitTarget != null) {
        val app = limitTarget!!
        val name = ctx.packageManager.getApplicationLabel(app).toString()
        AlertDialog(onDismissRequest={limitTarget=null}, title={Text("Daily usage limit")}, text={
            Column { Text(name); listOf(0,15,30,60,90,120,180).forEach { m ->
                TextButton(onClick={store.setDailyLimit(app.packageName,m);limitTarget=null;changed()}) { Text(if(m==0) "No daily limit" else "$m minutes per day") }
            } }
        }, confirmButton={TextButton(onClick={limitTarget=null}){Text("Close")}})
    }
}
private fun android.graphics.drawable.Drawable.toBitmapSafe(): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp); setBounds(0,0,96,96); draw(canvas); return bmp
}
@Composable private fun SitesPage(store:BlockStore, changed:()->Unit) {
    var site by remember { mutableStateOf("") }; var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp)) { OutlinedTextField(site,{site=it},Modifier.fillMaxWidth(),placeholder={Text("Add website, e.g. example.com")},singleLine=true); Button(onClick={if(site.isNotBlank()){store.addSite(site.trim());site="";tick++;changed()}},Modifier.fillMaxWidth().padding(top=8.dp)){Text("ADD WEBSITE")}; store.blockedSites().forEach { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(it,Modifier.weight(1f));IconButton(onClick={store.removeSite(it);tick++;changed()}){Icon(Icons.Default.Delete,null)}} } ; Text("Website filtering is applied only in browsers/apps whose page address is exposed to the enabled accessibility service.",fontSize=12.sp,color=Color.Gray) }
}
@Composable private fun KeywordsPage(store:BlockStore, changed:()->Unit) { var value by remember{mutableStateOf("")}; Column(Modifier.fillMaxSize().padding(16.dp)){OutlinedTextField(value,{value=it},Modifier.fillMaxWidth(),placeholder={Text("Add Keyword (comma-separated)")}); Button(onClick={value.split(",").map{it.trim()}.filter{it.isNotEmpty()}.forEach{store.addKeyword(it)};value="";changed()},Modifier.fillMaxWidth()){Text("Save")}; if(store.keywords().isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("NO KEYWORDS ADDED",color=Color.Gray)} else store.keywords().forEach{Text("•  $it",Modifier.padding(10.dp))} } }

@Composable private fun NotificationBlockingPage(ctx: Context, store: BlockStore, changed: () -> Unit) {
    val apps = remember {
        ctx.packageManager.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName }
            .sortedBy { ctx.packageManager.getApplicationLabel(it).toString().lowercase() }
    }
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Notification Blocking", fontSize=22.sp, fontWeight=FontWeight.Bold, color=Ink)
        Text("Select apps whose notifications should be dismissed while Focus Guard has Notification Access.", fontSize=12.sp, color=Color.Gray)
        Button(onClick={ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier=Modifier.fillMaxWidth().padding(vertical=12.dp)) {
            Text("Enable Notification Access")
        }
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Search apps")},singleLine=true)
        LazyColumn {
            items(apps.filter { ctx.packageManager.getApplicationLabel(it).toString().contains(query,true) }) { app ->
                val label=ctx.packageManager.getApplicationLabel(app).toString()
                val selected=app.packageName in store.notificationBlockedApps()
                Row(Modifier.fillMaxWidth().clickable { if(selected) store.removeNotificationBlockedApp(app.packageName) else store.addNotificationBlockedApp(app.packageName); changed() }.padding(vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(label,fontWeight=FontWeight.Medium); Text(app.packageName,fontSize=10.sp,color=Color.Gray) }
                    Checkbox(selected,{ if(it) store.addNotificationBlockedApp(app.packageName) else store.removeNotificationBlockedApp(app.packageName); changed() })
                }
                HorizontalDivider(color=Color(0xFFE9EAF0))
            }
        }
    }
}

@Composable private fun StrictPage(store:BlockStore, changed:()->Unit) {
    var enabled by remember{mutableStateOf(store.isStrict())}; var step by remember{mutableIntStateOf(0)}
    val titles=listOf("Blocking Level","Restriction Editing","Extra Restrictions","Deactivation Method","Activate Device Admin","Draw over other apps Permission")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(if(step==0)"Activate Lock Mode" else "Activate Strict Mode",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink)
        Text("Configure the restrictions you want to enable.",color=Color.Gray,fontSize=12.sp)
        titles.forEachIndexed { i,t ->
            Card(Modifier.fillMaxWidth().padding(top=9.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(14.dp)) {
                Text("${i+1}   $t",fontWeight=FontWeight.SemiBold)
                if(i==0) { Text("Choose what will be blocked.",fontSize=12.sp,color=Color.Gray); Row(verticalAlignment=Alignment.CenterVertically){Text("Stay Focused",Modifier.weight(1f));Switch(enabled,{enabled=it;store.setStrict(it);changed()})} }
                else if(i==1) Text("Prevent changes to blocking restrictions.",fontSize=12.sp,color=Color.Gray)
                else Text("Additional device-level permissions may be required.",fontSize=12.sp,color=Color.Gray)
            } }
        }
        Button(onClick={if(step<titles.lastIndex)step++ else {store.setStrict(enabled);changed()}},Modifier.fillMaxWidth().padding(top=16.dp)){Text(if(step<titles.lastIndex)"Next" else "Activate")}
        if(step>0) TextButton(onClick={step--}){Text("Back")}
    }
}
@Composable private fun TimerPage() { var minutes by remember{mutableIntStateOf(25)}; var seconds by remember{mutableIntStateOf(0)}; var running by remember{mutableStateOf(false)}; LaunchedEffect(running){while(running && (minutes>0||seconds>0)){delay(1000);if(seconds==0){minutes--;seconds=59}else seconds--};if(minutes==0&&seconds==0)running=false}; Column(Modifier.fillMaxSize().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("Study Timer",fontSize=24.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(32.dp)); Text("%02d:%02d".format(minutes,seconds),fontSize=64.sp,fontWeight=FontWeight.Light,color=Blue); Text("Focus session • 25 / 5",color=Color.Gray); Spacer(Modifier.height(24.dp)); Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick={running=!running}){Text(if(running)"Pause" else "Start")}; OutlinedButton(onClick={running=false;minutes=25;seconds=0}){Text("Reset")}} } }
@Composable private fun SchedulePage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { BlockStore(context) }
    var enabled by remember { mutableStateOf(store.scheduleEnabled()) }
    var startHour by remember { mutableIntStateOf(store.scheduleStartMinute() / 60) }
    var endHour by remember { mutableIntStateOf(store.scheduleEndMinute() / 60) }
    var startMinute by remember { mutableIntStateOf(store.scheduleStartMinute() % 60) }
    var endMinute by remember { mutableIntStateOf(store.scheduleEndMinute() % 60) }
    val dayLabels = listOf("M","T","W","T","F","S","S")
    val dayValues = listOf(2,3,4,5,6,7,1)
    val savedDays = store.scheduleDays()
    val selected = remember { mutableStateListOf(*dayValues.map { it in savedDays }.toTypedArray()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Set Blocking Schedule",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Ink)
        Text("Define when the block schedule should be active.",color=Color.Gray,fontSize=12.sp)
        Card(Modifier.padding(top=14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text("App Launch",Modifier.weight(1f)); Switch(enabled,{enabled=it;store.setScheduleEnabled(it)}) }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Notification",Modifier.weight(1f)); Switch(store.feature("schedule_notification"),{store.setFeature("schedule_notification",it)}) }
            Text("On Days",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { dayLabels.forEachIndexed { i,d -> FilterChip(selected=selected[i],onClick={selected[i]=!selected[i]},label={Text(d)}) } }
            Text("In Intervals",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp))
            Text("Start: ${"%02d:%02d".format(startHour,startMinute)}")
            Slider(startHour.toFloat(),{startHour=it.toInt()},valueRange=0f..23f,steps=22)
            Slider(startMinute.toFloat(),{startMinute=if(it<30) 0 else 30},valueRange=0f..30f,steps=0)
            Text("End: ${"%02d:%02d".format(endHour,endMinute)}")
            Slider(endHour.toFloat(),{endHour=it.toInt()},valueRange=0f..23f,steps=22)
            Slider(endMinute.toFloat(),{endMinute=if(it<30) 0 else 30},valueRange=0f..30f,steps=0)
            Button(onClick={store.setSchedule(startHour*60+startMinute,endHour*60+endMinute,dayValues.filterIndexed{i,_->selected[i]}.toSet());store.setScheduleEnabled(enabled)},Modifier.fillMaxWidth()){Text("Save")}
        } }
        Text("Schedule activates only while this app's Accessibility service is enabled. Android battery restrictions may affect reliability.",fontSize=11.sp,color=Color.Gray,modifier=Modifier.padding(top=10.dp))
    }
}
@Composable private fun ReelsPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { BlockStore(context) }
    val items = listOf("YouTube" to "Shorts", "Instagram" to "Reels", "Instagram" to "Stories", "WhatsApp" to "Channels", "Snapchat" to "Spotlight", "Facebook" to "Reels")
    val keys = listOf("yt_shorts","ig_reels","ig_stories","wa_channels","snap_spotlight","fb_reels")
    val states = remember { mutableStateListOf(*keys.mapIndexed { i,k -> store.feature(k, i==0 || i==1 || i==4) }.toTypedArray()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Block Reels/Shorts",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Ink)
        Text("Block distracting short-form feeds in supported apps.",fontSize=12.sp,color=Color.Gray)
        items.forEachIndexed { i,item ->
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical=11.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item.first,fontWeight=FontWeight.SemiBold); Text(item.second,fontSize=12.sp,color=Color.Gray) }
                    Switch(states[i],{states[i]=it;store.setFeature(keys[i],it)})
                }
                HorizontalDivider(color=Color(0xFFE9EAF0))
            }
        }
        Text("Content-level blocking depends on the target app's visible accessibility labels and can change after app updates.",fontSize=11.sp,color=Color.Gray,modifier=Modifier.padding(top=12.dp))
    }
}
@Composable private fun BreakPage() {
    var duration by remember{mutableIntStateOf(30)}; var remaining by remember{mutableIntStateOf(0)}; var running by remember{mutableStateOf(false)}
    LaunchedEffect(running) { while(running && remaining>0) { delay(1000); remaining-- }; if(remaining<=0) running=false }
    Column(Modifier.fillMaxSize().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Text("Take a Break",fontSize=22.sp,fontWeight=FontWeight.Bold); Text("Take a break from your phone",color=Color.Gray)
        Spacer(Modifier.height(24.dp)); Text(if(remaining>0) "%02d:%02d".format(remaining/60,remaining%60) else "$duration min",fontSize=48.sp,color=Blue)
        Slider(duration.toFloat(),{duration=it.toInt()},valueRange=5f..120f,steps=22,enabled=!running)
        Text("Break duration: $duration minutes",color=Color.Gray)
        Button(onClick={if(running) running=false else {remaining=duration*60;running=true}},Modifier.fillMaxWidth()){Text(if(running)"Pause" else "Start Break")}
        TextButton(onClick={running=false;remaining=0}){Text("Reset")}
    }
}
@Composable private fun BlockingTypePage(next:(String)->Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { BlockStore(context) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Choose Blocking Type",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink)
        listOf("Blocklist" to "Selected apps, websites, and keywords are blocked.", "Whitelist" to "Only apps you explicitly allow can be opened while the schedule is active.").forEach { (title,desc) ->
            Card(modifier=Modifier.fillMaxWidth().padding(top=12.dp),onClick={store.setWhitelistMode(title=="Whitelist");next("Choose Blocking Condition")},colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(18.dp)){Text(title,fontWeight=FontWeight.Bold);Text(desc,color=Color.Gray,fontSize=12.sp);Text(if(store.isWhitelistMode()==(title=="Whitelist"))"Selected" else "Select",fontSize=11.sp,color=Blue,modifier=Modifier.padding(top=8.dp))} }
        }
    }
}
@Composable private fun ProfilePage(next:(String)->Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Create New Profile",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink)
        Text("Personalize your focus by giving your profile a unique name.",fontSize=12.sp,color=Color.Gray)
        OutlinedTextField(name,{name=it},Modifier.fillMaxWidth().padding(top=16.dp),placeholder={Text("e.g. Sleep, Work, or Personal")},singleLine=true)
        Spacer(Modifier.weight(1f))
        Button(onClick={if(name.isNotBlank()){ BlockStore(context).addProfile(name); BlockStore(context).setActiveProfile(name); next("Choose Blocking Type") }},Modifier.fillMaxWidth(),enabled=name.isNotBlank()){Text("Save Profile & Next")}
    }
}
@Composable private fun ConditionPage(next:(String)->Unit) {
    val options=listOf("Usage Limit" to "e.g. 30 minutes per day or 5 minutes per session", "Specific Time Intervals" to "e.g. 9 AM to 6 PM", "Quick Block" to "e.g. next 10 minutes", "Number of Launches" to "e.g. 2 launches per hour", "Wait Timer" to "e.g. wait 30 minutes for every 5 minutes", "Goal Based" to "e.g. block until a daily goal is reached", "Location" to "e.g. block apps at school or office")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Choose Blocking Condition",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Ink)
        Text("Pick a method to apply the restrictions.",fontSize=12.sp,color=Color.Gray)
        options.forEach { (title,desc) -> Card(modifier=Modifier.fillMaxWidth().padding(top=9.dp),onClick={next(if(title=="Location")"Location Blocking" else if(title=="Usage Limit")"All Apps" else "Blocking Schedule")},colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(14.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(desc,fontSize=12.sp,color=Color.Gray)} } }
    }
}
@Composable private fun AnalyticsPage(ctx:Context) {
    var granted by remember { mutableStateOf(hasUsageAccess(ctx)) }
    var selected by remember { mutableStateOf("Screen Time") }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5000); granted = hasUsageAccess(ctx); refresh++ } }
    val report = remember(granted, refresh) {
        if (!granted) UsageReport(emptyList(), emptyMap(), 0, emptyList()) else buildUsageReport(ctx)
    }
    val usage = report.stats
    val total = usage.sumOf { it.totalTimeInForeground }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("Today",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink)
            TextButton(onClick={refresh++}) { Text("Refresh") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.padding(vertical=12.dp)) {
            listOf("Screen Time","App Launches","Browsing Time","Screen Unlocks","Usage Timeline").forEach {
                FilterChip(selected==it,{selected=it},label={Text(it,fontSize=10.sp)})
            }
        }
        if(!granted) {
            Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("USAGE ACCESS REQUIRED",fontWeight=FontWeight.Bold,color=Blue)
                    Text("Grant Usage Access to view screen time, launches and activity timeline.",color=Color.Gray)
                    Button(onClick={ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Grant Usage Access")}
                }
            }
        } else when(selected) {
            "Screen Time" -> {
                Card(colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(18.dp)) {
                    Text("Total Screen Time",color=Color.Gray); Text(formatDuration(total),fontSize=28.sp,fontWeight=FontWeight.Bold,color=Blue)
                    Spacer(Modifier.height(12.dp)); usage.take(10).forEach { stat ->
                        val label=appLabel(ctx,stat.packageName)
                        Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=13.sp);Text(formatDuration(stat.totalTimeInForeground),fontSize=12.sp,color=Color.Gray)}
                        LinearProgressIndicator(progress={if(total>0) stat.totalTimeInForeground.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth().height(5.dp),color=Blue,trackColor=Color(0xFFE7EBF4))
                    }
                } }
            }
            "App Launches" -> ReportListCard("App launches today", "${report.launches.values.sum()} launches", report.launches.entries.sortedByDescending { it.value }.map { appLabel(ctx,it.key) to "${it.value} launches" })
            "Browsing Time" -> {
                val browsers = usage.filter { isBrowserPackage(it.packageName) }.sortedByDescending { it.totalTimeInForeground }
                ReportListCard("Browser foreground time", formatDuration(browsers.sumOf { it.totalTimeInForeground }), browsers.map { appLabel(ctx,it.packageName) to formatDuration(it.totalTimeInForeground) }, "This measures time with a browser app in the foreground, not exact time spent on websites.")
            }
            "Screen Unlocks" -> ReportListCard("Screen unlock events", "${report.unlocks} unlock events", emptyList(), "Android reports keyguard-hidden events where supported. Some device versions may not expose this data.")
            else -> ReportListCard("Recent app activity", "${report.timeline.size} events", report.timeline.map { appLabel(ctx,it.first) to it.second })
        }
    }
}

private data class UsageReport(
    val stats: List<android.app.usage.UsageStats>,
    val launches: Map<String, Int>,
    val unlocks: Int,
    val timeline: List<Pair<String, String>>
)

private fun buildUsageReport(ctx: Context): UsageReport {
    val start = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0)
    }.timeInMillis
    val end = System.currentTimeMillis()
    val manager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
    val stats = manager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY,start,end)
        .filter { it.totalTimeInForeground > 0 && it.packageName != ctx.packageName }
        .sortedByDescending { it.totalTimeInForeground }
    val launches = linkedMapOf<String,Int>()
    val timeline = mutableListOf<Pair<String,String>>()
    var unlocks = 0
    val events = manager.queryEvents(start,end)
    val event = android.app.usage.UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        when (event.eventType) {
            android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                val pkg = event.packageName ?: continue
                if (pkg != ctx.packageName) {
                    launches[pkg] = (launches[pkg] ?: 0) + 1
                    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(event.timeStamp))
                    timeline.add(pkg to "Opened at $time")
                }
            }
            android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
        }
    }
    return UsageReport(stats,launches,unlocks,timeline.takeLast(30).reversed())
}

@Composable private fun ReportListCard(title:String, headline:String, rows:List<Pair<String,String>>, note:String?=null) {
    Card(colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.fillMaxWidth().padding(18.dp)) {
        Text(title,color=Color.Gray); Text(headline,fontSize=27.sp,fontWeight=FontWeight.Bold,color=Blue)
        if (rows.isEmpty() && note == null) Text("No events recorded today.",color=Color.Gray,modifier=Modifier.padding(top=12.dp))
        rows.forEach { (name,value) -> Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text(name,Modifier.weight(1f),fontSize=13.sp); Text(value,fontSize=12.sp,color=Color.Gray) } }
        if (note != null) Text(note,fontSize=11.sp,color=Color.Gray,modifier=Modifier.padding(top=14.dp))
    } }
}

private fun appLabel(ctx:Context,pkg:String):String = try {
    ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg,0)).toString()
} catch (_:Exception) { pkg }
private fun isBrowserPackage(pkg:String):Boolean = pkg.contains("browser",true) || pkg.contains("chrome",true) || pkg.contains("firefox",true) || pkg.contains("opera",true) || pkg.contains("brave",true) || pkg.contains("edge",true) || pkg.contains("vivaldi",true)

private fun formatDuration(ms:Long):String { val min=ms/60000; val h=min/60; val m=min%60; return if(h>0) "$h hrs $m mins" else "$m mins" }
private fun hasUsageAccess(ctx:Context):Boolean { val ops=ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager; return ops.checkOpNoThrow("android:get_usage_stats",android.os.Process.myUid(),ctx.packageName)==AppOpsManager.MODE_ALLOWED }
@Composable private fun AccountPage(open:(String)->Unit) { Column(Modifier.fillMaxSize().padding(16.dp)){Text("Account",fontSize=24.sp,fontWeight=FontWeight.Bold);listOf("Settings","Strict Mode","Blocking Schedule","Manage Keywords","About").forEach{MenuRow(it){open(it)}}} }
@Composable private fun SettingsPage(ctx:Context) { Column(Modifier.fillMaxSize().padding(18.dp)){Text("Settings",fontSize=24.sp,fontWeight=FontWeight.Bold);MenuRow("Accessibility Permission"){ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};MenuRow("Usage Access"){ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};MenuRow("Notification Access"){ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};MenuRow("App Notification Settings"){ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}} }
@Composable private fun LocationBlockingPage(activity: ComponentActivity, store: BlockStore, changed: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(150f) }
    var search by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var status by remember { mutableStateOf("Add a place and select which apps should be blocked there.") }
    var permissionTick by remember { mutableIntStateOf(0) }
    val apps = remember {
        activity.packageManager.getInstalledApplications(0)
            .filter { it.packageName != activity.packageName && activity.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { activity.packageManager.getApplicationLabel(it).toString().lowercase() }
    }
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionTick++
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && Build.VERSION.SDK_INT >= 29) {
            status = "Foreground location allowed. Also allow background location to detect entry/exit when Focus Guard is closed."
        } else if (granted) {
            LocationGeofenceManager.sync(activity) { ok, message -> status = message ?: if (ok) "Geofences synced." else "Geofence sync failed." }
        } else status = "Location permission was not granted."
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionTick++
        if (granted) LocationGeofenceManager.sync(activity) { ok, message -> status = message ?: if (ok) "Geofences synced." else "Geofence sync failed." }
        else status = "Background location is needed for reliable geofence events while the app is not open. You can enable it in App permissions."
    }
    LaunchedEffect(permissionTick) {
        if (LocationGeofenceManager.hasForegroundPermission(activity) && LocationGeofenceManager.hasBackgroundPermission(activity)) {
            LocationGeofenceManager.sync(activity) { ok, message -> if (!message.isNullOrBlank()) status = message }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Location-based Blocking", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Create a circular zone around a school, office, or other place. Selected apps are blocked when Android reports that you entered the zone.", fontSize = 12.sp, color = Color.Gray)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Location permissions", fontWeight = FontWeight.SemiBold)
                Text("Fine location: ${if (LocationGeofenceManager.hasForegroundPermission(activity)) "Allowed" else "Not allowed"}  •  Background: ${if (LocationGeofenceManager.hasBackgroundPermission(activity)) "Allowed" else "Not allowed"}", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { foregroundPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Allow location") }
                    if (Build.VERSION.SDK_INT >= 29) OutlinedButton(onClick = {
                        if (LocationGeofenceManager.hasForegroundPermission(activity)) backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        else status = "Allow foreground location first."
                    }) { Text("Allow background") }
                    OutlinedButton(onClick = {
                        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${activity.packageName}")))
                    }) { Text("App settings") }
                }
                Text("Android may require enabling ‘Allow all the time’ from App info → Permissions → Location.", fontSize = 11.sp, color = Color.Gray)
            }
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Place name") }, placeholder = { Text("School / Office") }, singleLine = true)
        OutlinedTextField(latitude, { latitude = it }, Modifier.fillMaxWidth(), label = { Text("Latitude") }, placeholder = { Text("e.g. 23.8103") }, singleLine = true)
        OutlinedTextField(longitude, { longitude = it }, Modifier.fillMaxWidth(), label = { Text("Longitude") }, placeholder = { Text("e.g. 90.4125") }, singleLine = true)
        Text("Geofence radius: ${radius.toInt()} meters", fontWeight = FontWeight.Medium)
        Slider(value = radius, onValueChange = { radius = it }, valueRange = 50f..1000f, steps = 18)
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search apps") }, singleLine = true)
        Text("Choose apps to block", fontWeight = FontWeight.SemiBold)
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            apps.filter { activity.packageManager.getApplicationLabel(it).toString().contains(search, true) || it.packageName.contains(search, true) }
                .take(100).forEach { app ->
                    val label = activity.packageManager.getApplicationLabel(app).toString()
                    Row(Modifier.fillMaxWidth().clickable { selectedPackages = if (app.packageName in selectedPackages) selectedPackages - app.packageName else selectedPackages + app.packageName }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = app.packageName in selectedPackages, onCheckedChange = { checked -> selectedPackages = if (checked) selectedPackages + app.packageName else selectedPackages - app.packageName })
                        Column { Text(label, fontSize = 13.sp); Text(app.packageName, fontSize = 10.sp, color = Color.Gray) }
                    }
                }
        }
        Button(onClick = {
            val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull()
            if (name.isBlank() || lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0 || selectedPackages.isEmpty()) {
                status = "Enter a valid place name and coordinates, then select at least one app."
            } else {
                val rule = LocationRule(UUID.randomUUID().toString(), name.trim(), lat, lon, radius, selectedPackages)
                store.saveLocationRule(rule)
                if (LocationGeofenceManager.hasForegroundPermission(activity) && LocationGeofenceManager.hasBackgroundPermission(activity)) {
                    LocationGeofenceManager.sync(activity) { ok, message -> status = message ?: if (ok) "Location zone saved and registered." else "Saved, but registration failed." }
                } else status = "Location zone saved. Grant foreground and background location permissions to activate it."
                name = ""; latitude = ""; longitude = ""; selectedPackages = emptySet(); changed()
            }
        }, Modifier.fillMaxWidth()) { Text("Save Location Zone") }
        Text(status, fontSize = 12.sp, color = Blue)
        Text("Saved zones", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        store.locationRules().forEach { rule ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(rule.name, fontWeight = FontWeight.SemiBold)
                    Text("${rule.latitude}, ${rule.longitude} • ${rule.radiusMeters.toInt()} m • ${rule.blockedPackages.size} app(s)", fontSize = 11.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { store.removeLocationRule(rule.id); LocationGeofenceManager.sync(activity) { _, message -> status = message ?: "Geofences updated." }; changed() }) { Text("Remove", color = Color.Red) }
                        TextButton(onClick = { LocationGeofenceManager.sync(activity) { _, message -> status = message ?: "Geofences updated." } }) { Text("Sync") }
                    }
                }
            }
        }
        Text("Geofencing is provided by Google Play services. Android may delay events due to battery saver, location settings, network conditions, or OEM background limits. Test on the target phone before relying on it.", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable private fun GenericPage(title:String) {
    val context=androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ink)
        when(title) {
            "Location Blocking" -> { Text("Location-based blocking requires location permission and a geofence setup.",color=Color.Gray); Button(onClick={context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}){Text("Open Location Settings")} }
            else -> Text("This setting is not yet wired to an Android system action.",color=Color.Gray)
        }
    }
}
