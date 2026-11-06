package com.autodrive.launcher

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PhoneContact(val id: Long, val name: String, val number: String)
internal enum class ContactsPhase { LOCKED, LOADING, READY, UNAVAILABLE }
internal data class PhoneContactsState(val phase: ContactsPhase = ContactsPhase.LOCKED,
    val contacts: List<PhoneContact> = emptyList(), val limited: Boolean = false)

/** Reads only the standard local Contacts Provider; never initiates PBAP or reads vendor-private databases. */
internal object PhoneContacts {
    fun permitted(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context): PhoneContactsState {
        if (!permitted(context)) return PhoneContactsState()
        return try {
            val rows = mutableListOf<PhoneContact>()
            val seen = hashSetOf<Pair<Long, String>>()
            var limited = false
            val cursor = context.contentResolver.query(Phone.CONTENT_URI,
                arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER), null, null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")
                ?: return PhoneContactsState(ContactsPhase.UNAVAILABLE)
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val number = DialNumber.normalize(it.getString(2).orEmpty()) ?: continue
                    if (!seen.add(id to number)) continue
                    if (rows.size == 5_000) { limited = true; break }
                    rows += PhoneContact(id, it.getString(1)?.takeIf(String::isNotBlank) ?: number, number)
                }
            }
            PhoneContactsState(ContactsPhase.READY, rows, limited)
        } catch (_: SecurityException) { PhoneContactsState() }
        catch (_: RuntimeException) { PhoneContactsState(ContactsPhase.UNAVAILABLE) }
    }
}

internal class PhoneContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(PhoneContactsState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh(debounce = true) }
    }

    fun start() {
        val app = getApplication<Application>()
        if (PhoneContacts.permitted(app) && !observing) {
            observing = runCatching {
                app.contentResolver.registerContentObserver(Phone.CONTENT_URI, true, observer)
                true
            }.getOrDefault(false)
        }
        refresh()
    }

    fun refresh(debounce: Boolean = false) {
        job?.cancel()
        val app = getApplication<Application>()
        if (!PhoneContacts.permitted(app)) { mutable.value = PhoneContactsState(); return }
        job = scope.launch {
            if (debounce) delay(300)
            mutable.value = PhoneContactsState(ContactsPhase.LOADING)
            mutable.value = withContext(Dispatchers.IO) { PhoneContacts.read(app) }
        }
    }

    fun stop() {
        job?.cancel()
        if (observing) runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(observer) }
        observing = false
        mutable.value = PhoneContactsState()
    }

    override fun onCleared() { stop(); scope.cancel(); super.onCleared() }
}
