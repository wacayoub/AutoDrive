package com.autodrive.launcher

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit write/read invocations separated by force-stop (also usable across an APK update). */
@RunWith(AndroidJUnit4::class)
class MapDisplayProcessTest {
    @Test fun displayChoicesSurviveAnotherProcess() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val phase=InstrumentationRegistry.getArguments().getString("phase")
        org.junit.Assume.assumeTrue(phase in setOf("write","read"))
        val prefs=context.getSharedPreferences("maps_display",0)
        val backup=File(context.filesDir,"map-process-test-backup.json")
        val memory=MapDisplayMemory(context)
        if(phase=="write"){
            check(!backup.exists()) { "Complete previous read/restore phase before seeding again" }
            backup.writeText(JSONObject().put("pid",Process.myPid()).put("previous",prefs.getString("state",null)).toString())
            memory.capture("""{"schema":1,"url":"https://www.google.com/maps/@33.57,-7.58,14z","options":{"TRAFFIC":true,"SATELLITE":true},"followOnStart":true}""")
            assertTrue(memory.read().followOnStart)
            Thread.sleep(1000)
        }else{
            val before=JSONObject(backup.readText())
            try{
                assertNotEquals("Test requires a new process",before.getInt("pid"),Process.myPid())
                val read=memory.read()
                assertTrue(read.followOnStart);assertEquals(true,read.options[MapDisplayOption.TRAFFIC]);assertEquals(true,read.options[MapDisplayOption.SATELLITE])
                assertFalse(read.launchUrl().contains("33.57"))
            }finally{
                prefs.edit().clear().apply{if(!before.isNull("previous"))putString("state",before.getString("previous"))}.commit()
                backup.delete()
            }
        }
    }
}
