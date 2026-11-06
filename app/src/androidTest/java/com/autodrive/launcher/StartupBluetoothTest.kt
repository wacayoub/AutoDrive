package com.autodrive.launcher

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.telecom.PhoneAccountHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBluetoothTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun bootReservationSurvivesRecreationAndRespectsDisabledAndMissingCounter() {
        val prefs = context.getSharedPreferences("radio_startup",0); val old = prefs.all
        try {
            prefs.edit().clear().commit()
            var count = 100
            assertTrue(RadioStartup(context) { count }.reserve())
            assertFalse(RadioStartup(context) { count }.reserve())
            count++
            val next = RadioStartup(context) { count }
            assertTrue(next.reserve()); next.enabled=false
            count++; assertFalse(next.reserve())
            next.enabled=true; assertTrue(next.reserve())
            assertFalse(RadioStartup(context) { null }.reserve())
        } finally {
            prefs.edit().clear().apply { old.forEach { (k,v)-> when(v){is Boolean->putBoolean(k,v);is Int->putInt(k,v)} } }.commit()
        }
    }
    @Test fun onlyKnownSystemHfpAccountsAreEligible() {
        fun valid(pkg:String="com.android.bluetooth",service:String=BluetoothCalls.SERVICE,system:Boolean=true,
            binding:String?=android.Manifest.permission.BIND_TELECOM_CONNECTION_SERVICE,enabled:Boolean=true,
            provider:Boolean=true,sim:Boolean=false,scheme:String?="hfpc",tel:Boolean=true)=
            BluetoothCalls.eligible(pkg,service,system,binding,enabled,provider,sim,scheme,tel)
        assertTrue(valid()); assertFalse(valid(pkg="com.android.phone"));assertFalse(valid(service="test.Service"))
        assertFalse(valid(system=false));assertFalse(valid(binding=null));assertFalse(valid(enabled=false))
        assertFalse(valid(provider=false));assertFalse(valid(sim=true));assertFalse(valid(scheme="tel"));assertFalse(valid(tel=false))
    }
    @Test fun actualTelecomWithoutHfpDoesNotOfferASimAsBluetooth() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        BluetoothCalls.permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName,it) }
        assertTrue(BluetoothCalls(context).accounts().isEmpty())
        val activity=instrumentation.startActivitySync(android.content.Intent(context,WebDashboardActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        fun views(view:android.view.View):List<android.view.View> = listOf(view)+if(view is android.view.ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
        try {
            instrumentation.runOnMainSync {
                views(activity.window.decorView).single{it.contentDescription=="Phone"}.performClick()
                views(activity.window.decorView).single{it.contentDescription=="Appels Bluetooth"}.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(views(activity.window.decorView).filterIsInstance<android.widget.TextView>().any{it.text.contains("Aucun compte Bluetooth HFP")})
                assertFalse(views(activity.window.decorView).any{it.contentDescription=="Appeler via Bluetooth"})
            }
            val image=instrumentation.uiAutomation.takeScreenshot()
            try{context.openFileOutput("phone-bluetooth-1.13.0.png",0).use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}finally{image.recycle()}
        } finally {instrumentation.runOnMainSync{activity.finish()}}
    }
    @Test fun directCallRechecksAccountPermissionsNumberAndNeverFallsBackToAnotherPhone() {
        val prefs=context.getSharedPreferences("bluetooth_calls",0);val old=prefs.all
        try {
            prefs.edit().clear().commit()
            var authorized=true
            val access=object:ContextWrapper(context){override fun checkSelfPermission(permission:String)=if(authorized)PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED}
            val first=BluetoothCallAccount(PhoneAccountHandle(ComponentName("com.android.bluetooth",BluetoothCalls.SERVICE),"fixture-one"),"Fixture un")
            val second=first.copy(handle=PhoneAccountHandle(first.handle.componentName,"fixture-two"),label="Fixture deux")
            var routes=listOf(first);var emergencies=false;var allowed=true;var queries=0
            val requests=mutableListOf<Pair<String,PhoneAccountHandle>>()
            val fake=object:BluetoothCallTransport{
                override fun accounts():List<BluetoothCallAccount>{queries++;return routes}
                override fun emergency(number:String)=emergencies
                override fun permitted(handle:PhoneAccountHandle)=allowed
                override fun place(number:String,handle:PhoneAccountHandle){requests+=number to handle}
            }
            val calls=BluetoothCalls(access,fake)
            assertNull(calls.call("+212 (6)12 34 56 78",first.handle))
            assertEquals(listOf("+212612345678" to first.handle),requests)
            assertNotNull(calls.call("*123#"));assertNotNull(calls.call("12"))
            emergencies=true;assertNotNull(calls.call("112"));emergencies=false
            allowed=false;assertNotNull(calls.call("0612345678"));allowed=true
            routes=listOf(second);assertNotNull(calls.call("0612345678",first.handle))
            routes=listOf(first,second);assertNull(calls.current());calls.choose(first)
            assertEquals(first,calls.current());routes=listOf(second);assertNull(calls.current())
            calls.clearSelection();assertEquals(second,calls.current())
            val before=queries;authorized=false;assertTrue(calls.accounts().isEmpty());assertEquals(before,queries)
            assertNotNull(calls.call("0612345678"));assertEquals(1,requests.size)
        } finally {prefs.edit().clear().apply{old.forEach{(k,v)->if(v is String)putString(k,v)}}.commit()}
    }
}
