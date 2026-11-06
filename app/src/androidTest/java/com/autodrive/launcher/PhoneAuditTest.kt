package com.autodrive.launcher

import android.Manifest
import android.content.ComponentName
import android.content.ContentProviderOperation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneAuditTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun oemPhoneHasAutomaticPriorityButNeverOverridesAnExplicitChoice() = withPhonePreferences {
        withActivity { activity -> instrumentation.runOnMainSync {
            val links = AppLinks(activity)
            val oem = Launchable(AppLinks.OEM_PHONE, "BT Phone")
            val other = Launchable(ComponentName("test.other", "test.other.Main"), "Autre")
            assertEquals(oem, links.phoneApp(listOf(other, oem)))
            assertNull(links.phoneApp(listOf(other)))
            assertNull(links.dialIntent("12345"))
            assertFalse(links.openPhone()) // No generic/SIM fallback without a selected Bluetooth app.
            links.saveAssociation(AppLinks.PHONE, other.component)
            assertEquals(other, links.phoneApp(listOf(oem, other)))
            assertNull(links.phoneApp(listOf(oem)))
            links.saveAssociation(AppLinks.PHONE, ComponentName("test.missing", "test.missing.Phone"))
            assertNull(links.dialIntent("12345"))
            assertFalse(links.openPhone())
        } }
    }

    @Test fun allKeypadKeysCursorSelectionReplacementAndExternalTypingWork() = withPhonePreferences {
        withActivity { activity ->
            lateinit var panel: PhonePanel
            instrumentation.runOnMainSync {
                click(activity.window.decorView, "Phone")
                panel = descendants(activity.window.decorView).filterIsInstance<PhonePanel>().single()
                assertTrue(panel.number.showSoftInputOnFocus)
                "123456789*0#".forEach { click(panel, it.toString()) }
                assertEquals("123456789*0#", panel.number.text.toString())
                panel.number.setSelection(2, 5); click(panel, "5")
                assertEquals("1256789*0#", panel.number.text.toString())
                assertEquals(3, panel.number.selectionStart)
                panel.number.setSelection(5, 2); click(panel, "0")
                assertEquals("12089*0#", panel.number.text.toString())
                panel.number.setSelection(0); click(panel, "Effacer un chiffre")
                assertEquals("12089*0#", panel.number.text.toString())
                panel.number.setText("1".repeat(40)); panel.number.setSelection(3, 7); click(panel, "5")
                assertEquals(37, panel.number.text.length)
                panel.number.setSelection(3, 4); click(panel, "Effacer un chiffre")
                assertEquals(36, panel.number.text.length)
                descendants(panel).single { it.contentDescription == "Effacer un chiffre" }.performLongClick()
                descendants(panel).single { it.contentDescription == "0" }.performLongClick()
                assertEquals("+", panel.number.text.toString())
                panel.number.setText("12")
                android.text.Selection.removeSelection(panel.number.text)
                click(panel, "5")
                assertEquals("125", panel.number.text.toString())
                panel.number.setText(""); panel.number.requestFocus()
            }
            instrumentation.sendStringSync("06555")
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals("06555", panel.number.text.toString())
                assertEquals("06555", RadioAndPhoneStore(activity).phoneDraft)
                panel.number.setText("")
            }
            instrumentation.waitForIdleSync()
            tap(descendant(activity.window.decorView, "5"))
            instrumentation.runOnMainSync { assertEquals("5", panel.number.text.toString()) }
            tap(panel.number)
            await { panel.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true }
        }
    }

    @Test fun deniedContactsDoNotQueryOrPretendToBeBluetoothSynced() = withPhonePreferences {
        val denied = object : ContextWrapper(context) {
            override fun checkSelfPermission(permission: String) = PackageManager.PERMISSION_DENIED
            override fun getContentResolver(): android.content.ContentResolver = error("Must not query without permission")
        }
        assertEquals(ContactsPhase.LOCKED, PhoneContacts.read(denied).phase)
        withActivity { activity -> instrumentation.runOnMainSync {
            click(activity.window.decorView, "Phone")
            val panel = descendants(activity.window.decorView).filterIsInstance<PhonePanel>().single()
            panel.render(PhoneContactsState())
            assertNotNull(descendants(panel).single { it.contentDescription == "Autoriser les contacts" })
            panel.render(PhoneContactsState(ContactsPhase.READY))
            assertTrue(descendants(panel).filterIsInstance<android.widget.TextView>().any { it.text.contains("Aucun contact partagé") })
            panel.render(PhoneContactsState(ContactsPhase.UNAVAILABLE))
            assertTrue(descendants(panel).filterIsInstance<android.widget.TextView>().any { it.text.contains("inaccessible") })
        } }
    }

    @Test fun standardContactsLoadSearchSelectAndObserveActualProviderChanges() = withPhonePreferences {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_CONTACTS)
        withSeededContact { rawId ->
            val initial = PhoneContacts.read(context)
            val fixture = initial.contacts.filter { it.name == "Contact test AutoDrive" }
            assertEquals(listOf("+212600000001", "+212600000002"), fixture.map { it.number }.sorted())
            withActivity { activity ->
                lateinit var panel: PhonePanel
                lateinit var model: PhoneContactsViewModel
                instrumentation.runOnMainSync {
                    click(activity.window.decorView, "Phone")
                    panel = descendants(activity.window.decorView).filterIsInstance<PhonePanel>().single()
                    model = ViewModelProvider(activity)[PhoneContactsViewModel::class.java]
                }
                await { model.state.value.contacts.any { it.name == "Contact test AutoDrive" } }
                instrumentation.runOnMainSync {
                    val search = descendants(panel).filterIsInstance<EditText>().single { it.contentDescription == "Rechercher un contact" }
                    search.setText("Contact test AutoDrive")
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val list = descendants(panel).filterIsInstance<ListView>().single()
                    assertEquals(2, list.count)
                    list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                    assertTrue(panel.number.text.toString().startsWith("+21260000000"))
                    val search = descendants(panel).filterIsInstance<EditText>().single { it.contentDescription == "Rechercher un contact" }
                    search.setText("000 002")
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(1, descendants(panel).filterIsInstance<ListView>().single().count)
                    val search = descendants(panel).filterIsInstance<EditText>().single { it.contentDescription == "Rechercher un contact" }
                    search.setText("absent fixture")
                    assertTrue(descendants(panel).filterIsInstance<android.widget.TextView>().any { it.text == "Aucun résultat" })
                    search.setText("")
                }
                // Synthetic fixture only; kept privately until adb pulls it for the QA artifact.
                instrumentation.waitForIdleSync()
                SystemClock.sleep(300) // Wait for SurfaceFlinger to present the cleared-search frame.
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                try {
                    context.openFileOutput("phone-contacts-1.7.0.png", 0).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
                withWriteIdentity {
                    val values = android.content.ContentValues().apply { put(Phone.NUMBER, "+212600000009") }
                    context.contentResolver.update(ContactsContract.Data.CONTENT_URI, values,
                        "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                        arrayOf(rawId.toString(), Phone.CONTENT_ITEM_TYPE))
                }
                await { model.state.value.contacts.any { it.name == "Contact test AutoDrive" && it.number == "+212600000009" } }
                instrumentation.runOnMainSync {
                    assertEquals(1, model.state.value.contacts.count { it.name == "Contact test AutoDrive" })
                    click(activity.window.decorView, "Back")
                    assertTrue(model.state.value.contacts.isEmpty())
                }
            }
        }
    }

    private fun withSeededContact(block: (Long) -> Unit) {
        var rawId = -1L
        withWriteIdentity {
            val ops = arrayListOf(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, "Contact test AutoDrive").build())
            for (number in listOf("+212600000001", "+212600000002", "+212 (6) 00000001")) {
                ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, number).build()
            }
            rawId = android.content.ContentUris.parseId(context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)[0].uri!!)
        }
        try { block(rawId) } finally { withWriteIdentity {
            context.contentResolver.delete(android.content.ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId), null, null)
        } }
    }
    private fun withWriteIdentity(block: () -> Unit) {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        try { block() } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }
    private fun await(check: () -> Boolean) {
        repeat(40) { var ready = false; instrumentation.runOnMainSync { ready = check() }; if (ready) return; SystemClock.sleep(100) }
        fail("Phone contacts state did not update")
    }
    private fun click(root: View, description: String) { descendants(root).single { it.contentDescription == description }.performClick() }
    private fun descendant(root: View, description: String): View {
        lateinit var found: View
        instrumentation.runOnMainSync { found = descendants(root).single { it.contentDescription == description } }
        return found
    }
    private fun tap(view: View) {
        val position = IntArray(2)
        var x = 0f; var y = 0f
        instrumentation.runOnMainSync {
            view.getLocationOnScreen(position)
            x = position[0] + view.width / 2f; y = position[1] + view.height / 2f
        }
        val now = SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        val up = android.view.MotionEvent.obtain(now, now + 50, android.view.MotionEvent.ACTION_UP, x, y, 0)
        try { instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up) }
        finally { down.recycle(); up.recycle() }
        instrumentation.waitForIdleSync()
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun withActivity(block: (WebDashboardActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(context, WebDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WebDashboardActivity
        try { block(activity) } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    private fun withPhonePreferences(block: () -> Unit) {
        val prefs = context.getSharedPreferences("radio_phone", 0)
        val old = prefs.all.toMap()
        val launcher = context.getSharedPreferences("launcher", 0)
        val oldPhone = launcher.getString("app_PHONE", null)
        prefs.edit().remove("phone_favorites").remove("phone_draft").commit()
        launcher.edit().remove("app_PHONE").commit()
        try { block() } finally {
            prefs.edit().clear().apply { old.forEach { (k, v) -> if (v is Boolean) putBoolean(k, v) else putString(k, v as? String) } }.commit()
            launcher.edit().putString("app_PHONE", oldPhone).commit()
        }
    }
}
