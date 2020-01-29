package yuku.alkitab.base

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Point
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.URLSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.HtmlCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import org.json.JSONException
import org.json.JSONObject
import yuku.afw.storage.Preferences
import yuku.alkitab.base.ac.GotoActivity
import yuku.alkitab.base.ac.MarkerListActivity
import yuku.alkitab.base.ac.MarkersActivity
import yuku.alkitab.base.ac.NoteActivity
import yuku.alkitab.base.ac.SearchActivity
import yuku.alkitab.base.ac.SettingsActivity
import yuku.alkitab.base.ac.ShareActivity
import yuku.alkitab.base.ac.VersionsActivity
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.dialog.ProgressMarkListDialog
import yuku.alkitab.base.dialog.ProgressMarkRenameDialog
import yuku.alkitab.base.dialog.TypeBookmarkDialog
import yuku.alkitab.base.dialog.TypeHighlightDialog
import yuku.alkitab.base.dialog.VersesDialog
import yuku.alkitab.base.dialog.XrefDialog
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.model.MVersionInternal
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.Announce
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Appearances
import yuku.alkitab.base.util.ClipboardUtil
import yuku.alkitab.base.util.CurrentReading
import yuku.alkitab.base.util.ExtensionManager
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.util.History
import yuku.alkitab.base.util.InstallationUtil
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.base.util.LidToAri
import yuku.alkitab.base.util.OtherAppIntegration
import yuku.alkitab.base.util.RequestCodes
import yuku.alkitab.base.util.ShareUrl
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.base.verses.VerseAttributeLoader
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.verses.VersesControllerImpl
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.base.verses.VersesListeners
import yuku.alkitab.base.verses.VersesUiModel
import yuku.alkitab.base.widget.AriParallelClickData
import yuku.alkitab.base.widget.DictionaryLinkInfo
import yuku.alkitab.base.widget.Floater
import yuku.alkitab.base.widget.FormattedTextRenderer
import yuku.alkitab.base.widget.GotoButton
import yuku.alkitab.base.widget.LabeledSplitHandleButton
import yuku.alkitab.base.widget.LeftDrawer
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper
import yuku.alkitab.base.widget.ParallelClickData
import yuku.alkitab.base.widget.ReferenceParallelClickData
import yuku.alkitab.base.widget.SplitHandleButton
import yuku.alkitab.base.widget.TextAppearancePanel
import yuku.alkitab.base.widget.TwofingerLinearLayout
import yuku.alkitab.base.widget.VerseInlineLinkSpan
import yuku.alkitab.base.widget.VerseRenderer
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book
import yuku.alkitab.model.Marker
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.ribka.RibkaReportActivity
import yuku.alkitab.tracking.Tracker
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import yuku.devoxx.flowlayout.FlowLayout
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.roundToLong

private const val TAG = "IsiActivity"
private const val EXTRA_verseUrl = "verseUrl"

class IsiActivity : BaseLeftDrawerActivity(), LeftDrawer.Text.Listener {
    var uncheckVersesWhenActionModeDestroyed = true
    var needsRestart = false // whether this activity needs to be restarted

    private val bGoto_floaterDrag = object : GotoButton.FloaterDragListener {
        val floaterLocationOnScreen = intArrayOf(0, 0)

        override fun onFloaterDragStart(screenX: Float, screenY: Float) {
            floater.show(activeBook.bookId, chapter_1)
            floater.onDragStart(S.activeVersion())
        }

        override fun onFloaterDragMove(screenX: Float, screenY: Float) {
            floater.getLocationOnScreen(floaterLocationOnScreen)
            floater.onDragMove(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1])
        }

        override fun onFloaterDragComplete(screenX: Float, screenY: Float) {
            floater.hide()
            floater.onDragComplete(screenX - floaterLocationOnScreen[0], screenY - floaterLocationOnScreen[1])
        }
    }

    private val floater_listener = Floater.Listener { ari ->
        jumpToAri(ari)
        history.add(ari)
    }

    private val splitRoot_listener = object : TwofingerLinearLayout.Listener {
        var startFontSize = 0f
        var startDx = Float.MIN_VALUE
        var chapterSwipeCellWidth = 0f // initted later
        var moreSwipeYAllowed = true // to prevent setting and unsetting fullscreen many times within one gesture

        override fun onOnefingerLeft() {
            Tracker.trackEvent("text_onefinger_left")
            bRight_click()
        }

        override fun onOnefingerRight() {
            Tracker.trackEvent("text_onefinger_right")
            bLeft_click()
        }

        override fun onTwofingerStart() {
            chapterSwipeCellWidth = 24f * resources.displayMetrics.density
            startFontSize = Preferences.getFloat(Prefkey.ukuranHuruf2, resources.getInteger(R.integer.pref_ukuranHuruf2_default).toFloat())
        }

        override fun onTwofingerScale(scale: Float) {
            var nowFontSize = startFontSize * scale

            if (nowFontSize < 2f) nowFontSize = 2f
            if (nowFontSize > 42f) nowFontSize = 42f

            Preferences.setFloat(Prefkey.ukuranHuruf2, nowFontSize)

            applyPreferences()

            textAppearancePanel?.displayValues()
        }

        override fun onTwofingerDragX(dx: Float) {
            if (startDx == Float.MIN_VALUE) { // just started
                startDx = dx

                if (dx < 0) {
                    bRight_click()
                } else {
                    bLeft_click()
                }
            } else { // more
                // more to the left
                while (dx < startDx - chapterSwipeCellWidth) {
                    startDx -= chapterSwipeCellWidth
                    bRight_click()
                }

                while (dx > startDx + chapterSwipeCellWidth) {
                    startDx += chapterSwipeCellWidth
                    bLeft_click()
                }
            }
        }

        override fun onTwofingerDragY(dy: Float) {
            if (!moreSwipeYAllowed) return

            if (dy < 0) {
                Tracker.trackEvent("text_twofinger_up")
                setFullScreen(true)
                leftDrawer.handle.setFullScreen(true)
            } else {
                Tracker.trackEvent("text_twofinger_down")
                setFullScreen(false)
                leftDrawer.handle.setFullScreen(false)
            }

            moreSwipeYAllowed = false
        }

        override fun onTwofingerEnd(mode: TwofingerLinearLayout.Mode?) {
            startFontSize = 0f
            startDx = Float.MIN_VALUE
            moreSwipeYAllowed = true
        }
    }

    lateinit var drawerLayout: DrawerLayout
    lateinit var leftDrawer: LeftDrawer.Text

    private lateinit var overlayContainer: FrameLayout
    lateinit var root: ViewGroup
    lateinit var toolbar: Toolbar
    lateinit var lsSplit0: VersesController
    lateinit var lsSplit1: VersesController
    lateinit var splitRoot: TwofingerLinearLayout
    lateinit var splitHandleButton: LabeledSplitHandleButton
    private lateinit var bGoto: GotoButton
    private lateinit var bLeft: ImageButton
    private lateinit var bRight: ImageButton
    private lateinit var bVersion: TextView
    lateinit var floater: Floater

    private var dataSplit0 = VersesDataModel.EMPTY
        set(value) {
            field = value
            lsSplit0.versesDataModel = value
        }

    private var dataSplit1 = VersesDataModel.EMPTY
        set(value) {
            field = value
            lsSplit1.versesDataModel = value
        }

    private var uiSplit0 = VersesUiModel.EMPTY
        set(value) {
            field = value
            lsSplit0.versesUiModel = value
        }

    private var uiSplit1 = VersesUiModel.EMPTY
        set(value) {
            field = value
            lsSplit1.versesUiModel = value
        }

    /**
     * There is always an active book.
     */
    lateinit var activeBook: Book
    var chapter_1 = 0
    private var fullScreen = false

    val history by lazy { History.getInstance() }

    /**
     * Can be null for devices without NfcAdapter
     */
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(applicationContext) }

    var actionMode: ActionMode? = null
    private var dictionaryMode = false
    var textAppearancePanel: TextAppearancePanel? = null

    /**
     * The following "esvsbasal" thing is a personal thing by yuku that doesn't matter to anyone else.
     * Please ignore it and leave it intact.
     */
    val hasEsvsbAsal by lazy {
        try {
            packageManager.getApplicationInfo("yuku.esvsbasal", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    data class ActiveSplit(
        val mv: MVersion,
        val version: Version,
        val versionId: String
    )

    var activeSplit: ActiveSplit? = null

    private val parallelListener: (data: ParallelClickData) -> Unit = { data ->
        if (data is ReferenceParallelClickData) {
            val ari = jumpTo(data.reference)
            if (ari != 0) {
                history.add(ari)
            }
        } else if (data is AriParallelClickData) {
            val ari = data.ari
            jumpToAri(ari)
            history.add(ari)
        }
    }

    private val dictionaryListener: (DictionaryLinkInfo) -> Unit = fun(data: DictionaryLinkInfo) {
        val cr = contentResolver
        val uri = Uri.parse("content://org.sabda.kamus.provider/define").buildUpon()
            .appendQueryParameter("key", data.key)
            .appendQueryParameter("mode", "snippet")
            .build()

        try {
            cr.query(uri, null, null, null, null) ?: run {
                OtherAppIntegration.askToInstallDictionary(this)
                return
            }
        } catch (e: Exception) {
            MaterialDialog.Builder(this)
                .content(R.string.dict_no_results)
                .positiveText(R.string.ok)
                .show()
            return
        }.use { c ->
            if (c.count == 0) {
                MaterialDialog.Builder(this)
                    .content(R.string.dict_no_results)
                    .positiveText(R.string.ok)
                    .show()
            } else {
                c.moveToNext()
                val rendered = HtmlCompat.fromHtml(c.getString(c.getColumnIndexOrThrow("definition")), HtmlCompat.FROM_HTML_MODE_COMPACT)
                val sb = if (rendered is SpannableStringBuilder) rendered else SpannableStringBuilder(rendered)

                // remove links
                for (span in sb.getSpans(0, sb.length, URLSpan::class.java)) {
                    sb.removeSpan(span)
                }

                MaterialDialog.Builder(this)
                    .title(data.orig_text)
                    .content(sb)
                    .positiveText(R.string.dict_open_full)
                    .onPositive { _, _ ->
                        val intent = Intent("org.sabda.kamus.action.VIEW")
                            .putExtra("key", data.key)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            OtherAppIntegration.askToInstallDictionary(this)
                        }
                    }
                    .show()
            }
        }
    }

    private val pinDropListener = object : VersesController.PinDropListener() {
        override fun onPinDropped(presetId: Int, ari: Int) {
            Tracker.trackEvent("pin_drop")

            val progressMark = S.getDb().getProgressMarkByPresetId(presetId)
            if (progressMark != null) {
                progressMark.ari = ari
                progressMark.modifyTime = Date()
                S.getDb().insertOrUpdateProgressMark(progressMark)
            }

            App.getLbm().sendBroadcast(Intent(ACTION_ATTRIBUTE_MAP_CHANGED))
        }
    }

    private val splitRoot_globalLayout = object : ViewTreeObserver.OnGlobalLayoutListener {
        val lastSize = Point()

        override fun onGlobalLayout() {
            if (lastSize.x == splitRoot.width && lastSize.y == splitRoot.height) {
                return  // no need to layout now
            }

            if (activeSplit == null) {
                return  // we are not splitting
            }

            configureSplitSizes()

            lastSize.x = splitRoot.width
            lastSize.y = splitRoot.height
        }
    }

    private val reloadAttributeMapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadBothAttributeMaps()
        }
    }

    private val needsRestartReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            needsRestart = true
        }
    }

    private val lsSplit0_selectedVerses = object : VersesController.SelectedVersesListener() {
        override fun onSomeVersesSelected(verses_1: IntArrayList) {
            if (activeSplit != null) {
                // synchronize the selection with the split view
                lsSplit1.checkVerses(verses_1, false)
            }

            if (actionMode == null) {
                actionMode = startSupportActionMode(actionMode_callback)
            }

            actionMode?.invalidate()
        }

        override fun onNoVersesSelected() {
            if (activeSplit != null) {
                // synchronize the selection with the split view
                lsSplit1.uncheckAllVerses(false)
            }

            actionMode?.finish()
            actionMode = null
        }
    }

    private val lsSplit1_selectedVerses = object : VersesController.SelectedVersesListener() {
        override fun onSomeVersesSelected(verses_1: IntArrayList) {
            // synchronize the selection with the main view
            lsSplit0.checkVerses(verses_1, true)
        }

        override fun onNoVersesSelected() {
            lsSplit0.uncheckAllVerses(true)
        }
    }

    private val lsSplit0_verseScroll = object : VersesController.VerseScrollListener() {
        override fun onVerseScroll(isPericope: Boolean, verse_1: Int, prop: Float) {
            if (!isPericope && activeSplit != null) {
                lsSplit1.scrollToVerse(verse_1, prop)
            }
        }

        override fun onScrollToTop() {
            if (activeSplit != null) {
                lsSplit1.scrollToTop()
            }
        }
    }

    private val lsSplit1_verseScroll = object : VersesController.VerseScrollListener() {
        override fun onVerseScroll(isPericope: Boolean, verse_1: Int, prop: Float) {
            if (!isPericope) {
                lsSplit0.scrollToVerse(verse_1, prop)
            }
        }

        override fun onScrollToTop() {
            lsSplit0.scrollToTop()
        }
    }

    val actionMode_callback = object : ActionMode.Callback {
        private val MENU_GROUP_EXTENSIONS = Menu.FIRST + 1
        private val MENU_EXTENSIONS_FIRST_ID = 0x1000

        val extensions = mutableListOf<ExtensionManager.Info>()

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.context_isi, menu)

            AppLog.d(TAG, "@@onCreateActionMode")

            if (hasEsvsbAsal) {
                val esvsb = menu.findItem(R.id.menuEsvsb)
                esvsb?.isVisible = true
            }

            // show book name and chapter
            val reference = activeBook.reference(chapter_1)
            mode.title = reference

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val menuAddBookmark = menu.findItem(R.id.menuAddBookmark)
            val menuAddNote = menu.findItem(R.id.menuAddNote)
            val menuCompare = menu.findItem(R.id.menuCompare)

            val selected = lsSplit0.getCheckedVerses_1()
            val single = selected.size() == 1

            // For unknown reasons the size of selected can be zero and get(0) causes crash.
            // https://console.firebase.google.com/u/0/project/alkitab-host-hrd/crashlytics/app/android:yuku.alkitab/issues/cc11d3466c89303f88b9e27ab3fdd534
            if (selected.size() == 0) {
                AppLog.e(TAG, "@@onPrepareActionMode checked verses is empty.")
                mode.finish()
                return true
            }

            var contiguous = true
            if (!single) {
                var next = selected.get(0) + 1
                var i = 1
                val len = selected.size()
                while (i < len) {
                    val cur = selected.get(i)
                    if (next != cur) {
                        contiguous = false
                        break
                    }
                    next = cur + 1
                    i++
                }
            }

            menuAddBookmark.isVisible = contiguous
            menuAddNote.isVisible = contiguous
            menuCompare.isVisible = single

            // just "copy" or ("copy primary" "copy secondary" "copy both")
            // same with "share".
            val menuCopy = menu.findItem(R.id.menuCopy)
            val menuCopySplit0 = menu.findItem(R.id.menuCopySplit0)
            val menuCopySplit1 = menu.findItem(R.id.menuCopySplit1)
            val menuCopyBothSplits = menu.findItem(R.id.menuCopyBothSplits)
            val menuShare = menu.findItem(R.id.menuShare)
            val menuShareSplit0 = menu.findItem(R.id.menuShareSplit0)
            val menuShareSplit1 = menu.findItem(R.id.menuShareSplit1)
            val menuShareBothSplits = menu.findItem(R.id.menuShareBothSplits)

            val split = activeSplit != null

            menuCopy.isVisible = !split
            menuCopySplit0.isVisible = split
            menuCopySplit1.isVisible = split
            menuCopyBothSplits.isVisible = split
            menuShare.isVisible = !split
            menuShareSplit0.isVisible = split
            menuShareSplit1.isVisible = split
            menuShareBothSplits.isVisible = split

            // show selected verses
            if (single) {
                mode.setSubtitle(R.string.verse_select_one_verse_selected)
            } else {
                mode.subtitle = getString(R.string.verse_select_multiple_verse_selected, selected.size().toString())
            }

            val menuGuide = menu.findItem(R.id.menuGuide)
            val menuCommentary = menu.findItem(R.id.menuCommentary)
            val menuDictionary = menu.findItem(R.id.menuDictionary)

            // force-show these items on sw600dp, otherwise never show
            val showAsAction = if (resources.configuration.smallestScreenWidthDp >= 600) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER
            menuGuide.setShowAsActionFlags(showAsAction)
            menuCommentary.setShowAsActionFlags(showAsAction)
            menuDictionary.setShowAsActionFlags(showAsAction)

            // set visibility according to appconfig
            val c = AppConfig.get()
            menuGuide.isVisible = c.menuGuide
            menuCommentary.isVisible = c.menuCommentary

            // do not show dictionary item if not needed because of auto-lookup from
            menuDictionary.isVisible = c.menuDictionary && !Preferences.getBoolean(getString(R.string.pref_autoDictionaryAnalyze_key), resources.getBoolean(R.bool.pref_autoDictionaryAnalyze_default))

            val menuRibkaReport = menu.findItem(R.id.menuRibkaReport)
            menuRibkaReport.isVisible = single && checkRibkaEligibility() != RibkaEligibility.None

            // extensions
            extensions.clear()
            extensions.addAll(ExtensionManager.getExtensions())

            menu.removeGroup(MENU_GROUP_EXTENSIONS)

            for ((i, extension) in extensions.withIndex()) {
                if (single || /* not single */ extension.supportsMultipleVerses) {
                    menu.add(MENU_GROUP_EXTENSIONS, MENU_EXTENSIONS_FIRST_ID + i, 0, extension.label)
                }
            }

            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selected = lsSplit0.getCheckedVerses_1()

            if (selected.size() == 0) return true

            return when (val itemId = item.itemId) {
                R.id.menuCopy, R.id.menuCopySplit0, R.id.menuCopySplit1, R.id.menuCopyBothSplits -> {

                    // copy, can be multiple verses

                    val reference = referenceFromSelectedVerses(selected, activeBook)
                    val activeSplit = activeSplit
                    val t = if (itemId == R.id.menuCopy || itemId == R.id.menuCopySplit0 || itemId == R.id.menuCopyBothSplits || activeSplit == null) {
                        prepareTextForCopyShare(selected, reference, false)
                    } else { // menuCopySplit1, do not use split0 reference
                        val splitBook = activeSplit.version.getBook(activeBook.bookId)
                        prepareTextForCopyShare(selected, referenceFromSelectedVerses(selected, splitBook ?: activeBook), true)
                    }

                    if (itemId == R.id.menuCopyBothSplits && activeSplit != null) { // put guard on activeSplitVersion
                        appendSplitTextForCopyShare(activeSplit.version, lsSplit1.getCheckedVerses_1(), t)
                    }

                    val textToCopy = t[0]
                    val textToSubmit = t[1]

                    ShareUrl.make(this@IsiActivity, !Preferences.getBoolean(getString(R.string.pref_copyWithShareUrl_key), resources.getBoolean(R.bool.pref_copyWithShareUrl_default)), textToSubmit, Ari.encode(activeBook.bookId, chapter_1, 0), selected, reference.toString(), S.activeVersion(), MVersionDb.presetNameFromVersionId(S.activeVersionId()), object : ShareUrl.Callback {
                        override fun onSuccess(shareUrl: String) {
                            ClipboardUtil.copyToClipboard(textToCopy + "\n\n" + shareUrl)
                        }

                        override fun onUserCancel() {
                            ClipboardUtil.copyToClipboard(textToCopy)
                        }

                        override fun onError(e: Exception) {
                            ClipboardUtil.copyToClipboard(textToCopy)
                        }

                        override fun onFinally() {
                            lsSplit0.uncheckAllVerses(true)

                            Snackbar.make(root, getString(R.string.alamat_sudah_disalin, reference), Snackbar.LENGTH_SHORT).show()
                            mode.finish()
                        }
                    })

                    true
                }
                R.id.menuShare, R.id.menuShareSplit0, R.id.menuShareSplit1, R.id.menuShareBothSplits -> {
                    // share, can be multiple verses
                    val reference = referenceFromSelectedVerses(selected, activeBook)
                    val activeSplit = activeSplit

                    val t = if (itemId == R.id.menuShare || itemId == R.id.menuShareSplit0 || itemId == R.id.menuShareBothSplits || activeSplit == null) {
                        prepareTextForCopyShare(selected, reference, false)
                    } else { // menuShareSplit1, do not use split0 reference
                        val splitBook = activeSplit.version.getBook(activeBook.bookId)
                        prepareTextForCopyShare(selected, referenceFromSelectedVerses(selected, splitBook ?: activeBook), true)
                    }

                    if (itemId == R.id.menuShareBothSplits && activeSplit != null) { // put guard on activeSplitVersion
                        appendSplitTextForCopyShare(activeSplit.version, lsSplit1.getCheckedVerses_1(), t)
                    }

                    val textToShare = t[0]
                    val textToSubmit = t[1]

                    val intent = ShareCompat.IntentBuilder.from(this@IsiActivity)
                        .setType("text/plain")
                        .setSubject(reference.toString())
                        .intent

                    ShareUrl.make(this@IsiActivity, !Preferences.getBoolean(getString(R.string.pref_copyWithShareUrl_key), resources.getBoolean(R.bool.pref_copyWithShareUrl_default)), textToSubmit, Ari.encode(activeBook.bookId, chapter_1, 0), selected, reference.toString(), S.activeVersion(), MVersionDb.presetNameFromVersionId(S.activeVersionId()), object : ShareUrl.Callback {
                        override fun onSuccess(shareUrl: String) {
                            intent.putExtra(Intent.EXTRA_TEXT, textToShare + "\n\n" + shareUrl)
                            intent.putExtra(EXTRA_verseUrl, shareUrl)
                        }

                        override fun onUserCancel() {
                            intent.putExtra(Intent.EXTRA_TEXT, textToShare)
                        }

                        override fun onError(e: Exception) {
                            intent.putExtra(Intent.EXTRA_TEXT, textToShare)
                        }

                        override fun onFinally() {
                            startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_alamat, reference)), RequestCodes.FromActivity.Share)

                            lsSplit0.uncheckAllVerses(true)
                            mode.finish()
                        }
                    })
                    true
                }
                R.id.menuCompare -> {
                    val ari = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, selected.get(0))
                    val dialog = VersesDialog.newCompareInstance(ari)
                    dialog.listener = object : VersesDialog.VersesDialogListener() {
                        override fun onComparedVerseSelected(ari: Int, mversion: MVersion) {
                            loadVersion(mversion)
                            dialog.dismiss()
                        }
                    }
                    dialog.show(supportFragmentManager, "compare_dialog")
                    true
                }
                R.id.menuAddBookmark -> {

                    // contract: this menu only appears when contiguous verses are selected
                    if (selected.get(selected.size() - 1) - selected.get(0) != selected.size() - 1) {
                        throw RuntimeException("Non contiguous verses when adding bookmark: $selected")
                    }

                    val ari = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, selected.get(0))
                    val verseCount = selected.size()

                    // always create a new bookmark
                    val dialog = TypeBookmarkDialog.NewBookmark(this@IsiActivity, ari, verseCount)
                    dialog.setListener {
                        lsSplit0.uncheckAllVerses(true)
                        reloadBothAttributeMaps()
                    }
                    dialog.show()

                    mode.finish()
                    true
                }
                R.id.menuAddNote -> {

                    // contract: this menu only appears when contiguous verses are selected
                    if (selected.get(selected.size() - 1) - selected.get(0) != selected.size() - 1) {
                        throw RuntimeException("Non contiguous verses when adding note: $selected")
                    }

                    val ari = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, selected.get(0))
                    val verseCount = selected.size()

                    // always create a new note
                    startActivityForResult(NoteActivity.createNewNoteIntent(S.activeVersion().referenceWithVerseCount(ari, verseCount), ari, verseCount), RequestCodes.FromActivity.EditNote2)
                    mode.finish()

                    true
                }
                R.id.menuAddHighlight -> {
                    val ariBc = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, 0)
                    val colorRgb = S.getDb().getHighlightColorRgb(ariBc, selected)

                    val listener = TypeHighlightDialog.Listener {
                        lsSplit0.uncheckAllVerses(true)
                        reloadBothAttributeMaps()
                    }

                    val reference = referenceFromSelectedVerses(selected, activeBook)
                    if (selected.size() == 1) {
                        val ftr = VerseRenderer.FormattedTextResult()
                        val ari = Ari.encodeWithBc(ariBc, selected.get(0))
                        val rawVerseText = S.activeVersion().loadVerseText(ari)
                        val info = S.getDb().getHighlightColorRgb(ari)

                        assert(rawVerseText != null)
                        VerseRenderer.render(null, null, ari, rawVerseText!!, "" + Ari.toVerse(ari), null, false, null, ftr)
                        TypeHighlightDialog(this@IsiActivity, ari, listener, colorRgb, info, reference, ftr.result)
                    } else {
                        TypeHighlightDialog(this@IsiActivity, ariBc, selected, listener, colorRgb, reference)
                    }
                    mode.finish()
                    true
                }
                R.id.menuEsvsb -> {

                    val ari = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, selected.get(0))

                    try {
                        val intent = Intent("yuku.esvsbasal.action.GOTO")
                        intent.putExtra("ari", ari)
                        startActivity(intent)
                    } catch (e: Exception) {
                        AppLog.e(TAG, "ESVSB starting", e)
                    }
                    true
                }
                R.id.menuGuide -> {

                    val ari = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, 0)

                    try {
                        packageManager.getPackageInfo("org.sabda.pedia", 0)

                        val intent = Intent("org.sabda.pedia.action.VIEW")
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra("ari", ari)
                        startActivity(intent)
                    } catch (e: PackageManager.NameNotFoundException) {
                        OtherAppIntegration.openMarket(this@IsiActivity, "org.sabda.pedia")
                    }
                    true
                }
                R.id.menuCommentary -> {

                    val ari = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, selected.get(0))

                    try {
                        packageManager.getPackageInfo("org.sabda.tafsiran", 0)

                        val intent = Intent("org.sabda.tafsiran.action.VIEW")
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra("ari", ari)
                        startActivity(intent)
                    } catch (e: PackageManager.NameNotFoundException) {
                        OtherAppIntegration.openMarket(this@IsiActivity, "org.sabda.tafsiran")
                    }
                    true
                }
                R.id.menuDictionary -> {

                    val ariBc = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, 0)
                    val aris = HashSet<Int>()
                    var i = 0
                    val len = selected.size()
                    while (i < len) {
                        val verse_1 = selected.get(i)
                        val ari = Ari.encodeWithBc(ariBc, verse_1)
                        aris.add(ari)
                        i++
                    }

                    startDictionaryMode(aris)
                    true
                }

                R.id.menuRibkaReport -> {

                    val ribkaEligibility = checkRibkaEligibility()
                    if (ribkaEligibility != RibkaEligibility.None) {
                        val ari = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, selected.get(0))

                        val reference: CharSequence?
                        val verseText: String?
                        val versionDescription: String?

                        if (ribkaEligibility == RibkaEligibility.Main) {
                            reference = S.activeVersion().reference(ari)
                            verseText = S.activeVersion().loadVerseText(ari)
                            versionDescription = S.activeMVersion().description
                        } else {
                            reference = activeSplit?.version?.reference(ari)
                            verseText = activeSplit?.version?.loadVerseText(ari)
                            versionDescription = activeSplit?.mv?.description
                        }

                        if (reference != null && verseText != null) {
                            startActivity(RibkaReportActivity.createIntent(ari, reference.toString(), verseText, versionDescription))
                        }
                    }
                    true
                }

                in MENU_EXTENSIONS_FIRST_ID until MENU_EXTENSIONS_FIRST_ID + extensions.size -> {
                    val extension = extensions[itemId - MENU_EXTENSIONS_FIRST_ID]

                    val intent = Intent(ExtensionManager.ACTION_SHOW_VERSE_INFO)
                    intent.component = ComponentName(extension.activityInfo.packageName, extension.activityInfo.name)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    // prepare extra "aris"
                    val aris = IntArray(selected.size())
                    val ariBc = Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, 0)
                    run {
                        var i = 0
                        val len = selected.size()
                        while (i < len) {
                            val verse_1 = selected.get(i)
                            val ari = Ari.encodeWithBc(ariBc, verse_1)
                            aris[i] = ari
                            i++
                        }
                    }
                    intent.putExtra("aris", aris)

                    if (extension.includeVerseText) {
                        // prepare extra "verseTexts"
                        val verseTexts = arrayOfNulls<String>(selected.size())
                        var i = 0
                        val len = selected.size()
                        while (i < len) {
                            val verse_1 = selected.get(i)

                            val verseText = dataSplit0.getVerseText(verse_1)
                            if (extension.includeVerseTextFormatting) {
                                verseTexts[i] = verseText
                            } else {
                                verseTexts[i] = FormattedVerseText.removeSpecialCodes(verseText)
                            }
                            i++
                        }
                        intent.putExtra("verseTexts", verseTexts)
                    }

                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        MaterialDialog.Builder(this@IsiActivity)
                            .content("Error ANFE starting extension\n\n" + extension.activityInfo.packageName + "/" + extension.activityInfo.name)
                            .positiveText(R.string.ok)
                            .show()
                    }

                    true
                }

                else -> false
            }
        }

        /**
         * @param t [0] is text to copy, [1] is text to submit
         */
        fun appendSplitTextForCopyShare(version: Version, selectedVerses_1: IntArrayList, t: Array<String>) {
            val splitBook = version.getBook(activeBook.bookId)
            if (splitBook != null) {
                val referenceSplit = referenceFromSelectedVerses(selectedVerses_1, splitBook)
                val a = prepareTextForCopyShare(selectedVerses_1, referenceSplit, true)
                t[0] += "\n\n" + a[0]
                t[1] += "\n\n" + a[1]
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null

            // FIXME even with this guard, verses are still unchecked when switching version while both Fullscreen and Split is active.
            // This guard only fixes unchecking of verses when in fullscreen mode.
            if (uncheckVersesWhenActionModeDestroyed) {
                lsSplit0.uncheckAllVerses(true)
            }
        }
    }

    private val splitHandleButton_listener = object : SplitHandleButton.SplitHandleButtonListener {
        var first = 0
        var handle = 0
        var root = 0
        var prop = 0f // proportion from top or left

        override fun onHandleDragStart() {
            splitRoot.setOnefingerEnabled(false)

            if (splitHandleButton.orientation == SplitHandleButton.Orientation.vertical) {
                first = splitHandleButton.top
                handle = splitHandleButton.height
                root = splitRoot.height
            } else {
                first = splitHandleButton.left
                handle = splitHandleButton.width
                root = splitRoot.width
            }

            prop = Float.MIN_VALUE // guard against glitches
        }

        override fun onHandleDragMoveX(dxSinceLast: Float, dxSinceStart: Float) {
            val newW = (first + dxSinceStart).toInt()
            val maxW = root - handle
            val width = if (newW < 0) 0 else if (newW > maxW) maxW else newW
            lsSplit0.setViewLayoutSize(width, ViewGroup.LayoutParams.MATCH_PARENT)
            prop = width.toFloat() / maxW
        }

        override fun onHandleDragMoveY(dySinceLast: Float, dySinceStart: Float) {
            val newH = (first + dySinceStart).toInt()
            val maxH = root - handle
            val height = if (newH < 0) 0 else if (newH > maxH) maxH else newH
            lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, height)
            prop = height.toFloat() / maxH
        }

        override fun onHandleDragStop() {
            splitRoot.setOnefingerEnabled(true)

            if (prop != Float.MIN_VALUE) {
                Preferences.setFloat(Prefkey.lastSplitProp, prop)
            }
        }
    }

    private val splitHandleButton_labelPressed = LabeledSplitHandleButton.ButtonPressListener { which ->
        when (which) {
            LabeledSplitHandleButton.Button.rotate -> {
                closeSplitDisplay()
                openSplitDisplay()
            }
            LabeledSplitHandleButton.Button.start -> openVersionsDialog()
            LabeledSplitHandleButton.Button.end -> openSplitVersionsDialog()
            else -> throw IllegalStateException("should not happen")
        }
    }

    data class IntentResult(
        val ari: Int,
        val selectVerse: Boolean = false,
        val selectVerseCount: Int = 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLog.d(TAG, "@@onCreate start")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_isi)
        AppLog.d(TAG, "@@onCreate setCV")

        drawerLayout = findViewById(R.id.drawerLayout)
        leftDrawer = findViewById(R.id.left_drawer)
        leftDrawer.configure(this, drawerLayout)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
            setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        }

        bGoto = findViewById(R.id.bGoto)
        bLeft = findViewById(R.id.bLeft)
        bRight = findViewById(R.id.bRight)
        bVersion = findViewById(R.id.bVersion)

        overlayContainer = findViewById(R.id.overlayContainer)
        root = findViewById(R.id.root)
        splitRoot = findViewById(R.id.splitRoot)
        splitRoot.viewTreeObserver.addOnGlobalLayoutListener(splitRoot_globalLayout)

        splitHandleButton = findViewById(R.id.splitHandleButton)
        floater = findViewById(R.id.floater)

        // If layout is changed, updateToolbarLocation must be updated as well. This will be called in DEBUG to make sure
        // updateToolbarLocation is also updated when layout is updated.
        if (BuildConfig.DEBUG) {
            if (root.childCount != 2 || root.getChildAt(0).id != R.id.toolbar || root.getChildAt(1).id != R.id.splitRoot) {
                throw RuntimeException("Layout changed and this is no longer compatible with updateToolbarLocation")
            }
        }

        updateToolbarLocation()

        splitRoot.setListener(splitRoot_listener)

        bGoto.setOnClickListener { bGoto_click() }
        bGoto.setOnLongClickListener {
            bGoto_longClick()
            true
        }
        bGoto.setFloaterDragListener(bGoto_floaterDrag)

        bLeft.setOnClickListener { bLeft_click() }
        bRight.setOnClickListener { bRight_click() }
        bVersion.setOnClickListener { bVersion_click() }

        floater.setListener(floater_listener)

        // TODO(VersesView revamp): Move it somewhere else
        //		lsSplit0.setOnKeyListener((v, keyCode, event) -> {
        //			int action = event.getAction();
        //			if (action == KeyEvent.ACTION_DOWN) {
        //				return consumeKey(keyCode);
        //			} else if (action == KeyEvent.ACTION_MULTIPLE) {
        //				return consumeKey(keyCode);
        //			}
        //			return false;
        //		});

        // listeners
        lsSplit0 = VersesControllerImpl(
            findViewById(R.id.lsSplitView0),
            "lsSplit0",
            VersesDataModel.EMPTY,
            VersesUiModel.EMPTY,
            VersesListeners(
                AttributeListener(), // have to be distinct from lsSplit1
                lsSplit0_selectedVerses,
                lsSplit0_verseScroll,
                parallelListener,
                VerseInlineLinkSpanFactory { lsSplit0 },
                dictionaryListener,
                pinDropListener
            )
        )

        // additional setup for split1
        lsSplit1 = VersesControllerImpl(
            findViewById(R.id.lsSplitView1),
            "lsSplit1",
            VersesDataModel.EMPTY,
            VersesUiModel.EMPTY,
            VersesListeners(
                AttributeListener(), // have to be distinct from lsSplit0
                lsSplit1_selectedVerses,
                lsSplit1_verseScroll,
                parallelListener,
                VerseInlineLinkSpanFactory { lsSplit1 },
                dictionaryListener,
                pinDropListener
            )
        )

        // for splitting
        splitHandleButton.setListener(splitHandleButton_listener)
        splitHandleButton.setButtonPressListener(splitHandleButton_labelPressed)

        if (BuildConfig.DEBUG) {
            // Runtime assertions: splitRoot must have 3 children;
            // lsSplitView0, splitHandleButton, lsSplitView1 in that order.
            if (splitRoot.childCount != 3) throw RuntimeException("splitRoot does not have correct children")
            if (splitRoot.getChildAt(0) !== splitRoot.findViewById<View>(R.id.lsSplitView0)) throw RuntimeException("splitRoot does not have correct children")
            if (splitRoot.getChildAt(1) !== splitRoot.findViewById<View>(R.id.splitHandleButton)) throw RuntimeException("splitRoot does not have correct children")
            if (splitRoot.getChildAt(2) !== splitRoot.findViewById<View>(R.id.lsSplitView1)) throw RuntimeException("splitRoot does not have correct children")
        }

        // migrate old history?
        History.migrateOldHistoryWhenNeeded()

        initNfcIfAvailable()

        val intentResult = processIntent(intent, "onCreate")
        val openingAri: Int
        val selectVerse: Boolean
        val selectVerseCount: Int

        if (intentResult == null) {
            // restore the last (version; book; chapter and verse).
            val lastBookId = Preferences.getInt(Prefkey.lastBookId, 0)
            val lastChapter = Preferences.getInt(Prefkey.lastChapter, 0)
            val lastVerse = Preferences.getInt(Prefkey.lastVerse, 0)
            openingAri = Ari.encode(lastBookId, lastChapter, lastVerse)
            selectVerse = false
            selectVerseCount = 1
            AppLog.d(TAG, "Going to the last: bookId=$lastBookId chapter=$lastChapter verse=$lastVerse")
        } else {
            openingAri = intentResult.ari
            selectVerse = intentResult.selectVerse
            selectVerseCount = intentResult.selectVerseCount
        }

        this.activeBook = S.activeVersion().getBook(Ari.toBook(openingAri)) ?: run {
            // can't load last book or bookId 0
            S.activeVersion().firstBook ?: run {
                // version failed to load, so books are also failed to load. Fallback!
                S.setActiveVersion(S.getMVersionInternal())
                S.activeVersion().firstBook // this is assumed to be never null
            }
        }

        // first display of active version
        displayActiveVersion()

        // load chapter and verse
        display(Ari.toChapter(openingAri), Ari.toVerse(openingAri))

        if (intentResult != null) { // also add to history if not opening the last seen verse
            history.add(openingAri)
        }

        run {
            // load last split version. This must be after load book, chapter, and verse.
            val lastSplitVersionId = Preferences.getString(Prefkey.lastSplitVersionId, null)
            if (lastSplitVersionId != null) {
                val splitOrientation = Preferences.getString(Prefkey.lastSplitOrientation)
                if (SplitHandleButton.Orientation.horizontal.name == splitOrientation) {
                    splitHandleButton.orientation = SplitHandleButton.Orientation.horizontal
                } else {
                    splitHandleButton.orientation = SplitHandleButton.Orientation.vertical
                }

                val splitMv = S.getVersionFromVersionId(lastSplitVersionId)
                val splitMvActual = splitMv ?: S.getMVersionInternal()

                if (loadSplitVersion(splitMvActual)) {
                    openSplitDisplay()
                    displaySplitFollowingMaster(Ari.toVerse(openingAri))
                }
            }
        }

        if (selectVerse) {
            for (i in 0 until selectVerseCount) {
                val verse_1 = Ari.toVerse(openingAri) + i
                callAttentionForVerseToBothSplits(verse_1)
            }
        }

        App.getLbm().registerReceiver(reloadAttributeMapReceiver, IntentFilter(ACTION_ATTRIBUTE_MAP_CHANGED))

        Announce.checkAnnouncements()

        App.getLbm().registerReceiver(needsRestartReceiver, IntentFilter(ACTION_NEEDS_RESTART))
        AppLog.d(TAG, "@@onCreate end")
    }

    private fun calculateTextSizeMult(versionId: String?): Float {
        return if (versionId == null) 1f else S.getDb().getPerVersionSettings(versionId).fontSizeMultiplier
    }

    private fun callAttentionForVerseToBothSplits(verse_1: Int) {
        lsSplit0.callAttentionForVerse(verse_1)
        lsSplit1.callAttentionForVerse(verse_1)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        processIntent(intent, "onNewIntent")
    }

    override fun onDestroy() {
        super.onDestroy()

        App.getLbm().unregisterReceiver(reloadAttributeMapReceiver)

        App.getLbm().unregisterReceiver(needsRestartReceiver)
    }

    /**
     * @return non-null if the intent is handled by any of the intent handler (e.g. nfc or VIEW)
     */
    private fun processIntent(intent: Intent, via: String): IntentResult? {
        dumpIntent(intent, via)

        return tryGetIntentResultFromBeam(intent)
            ?: tryGetIntentResultFromView(intent)
    }

    /**
     * did we get here from VIEW intent?
     */
    private fun tryGetIntentResultFromView(intent: Intent): IntentResult? {
        if (intent.action != "yuku.alkitab.action.VIEW") return null

        val selectVerse = intent.getBooleanExtra("selectVerse", false)
        val selectVerseCount = intent.getIntExtra("selectVerseCount", 1)

        return when {
            intent.hasExtra("ari") -> {
                val ari = intent.getIntExtra("ari", 0)
                if (ari != 0) {
                    IntentResult(ari, selectVerse, selectVerseCount)
                } else {
                    MaterialDialog.Builder(this)
                        .content("Invalid ari: $ari")
                        .positiveText(R.string.ok)
                        .show()
                    null
                }
            }
            intent.hasExtra("lid") -> {
                val lid = intent.getIntExtra("lid", 0)
                val ari = LidToAri.lidToAri(lid)
                if (ari != 0) {
                    jumpToAri(ari)
                    history.add(ari)
                    IntentResult(ari, selectVerse, selectVerseCount)
                } else {
                    MaterialDialog.Builder(this)
                        .content("Invalid lid: $lid")
                        .positiveText(R.string.ok)
                        .show()
                    null
                }
            }
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun initNfcIfAvailable() {
        nfcAdapter?.setNdefPushMessageCallback(NfcAdapter.CreateNdefMessageCallback {
            val obj = JSONObject()
            obj.put("ari", Ari.encode(this@IsiActivity.activeBook.bookId, this@IsiActivity.chapter_1, getVerse_1BasedOnScrolls()))

            val payload = obj.toString().toByteArray()
            val record = NdefRecord(NdefRecord.TNF_MIME_MEDIA, "application/vnd.yuku.alkitab.nfc.beam".toByteArray(), ByteArray(0), payload)
            NdefMessage(arrayOf(record, NdefRecord.createApplicationRecord(packageName)))
        }, this)
    }

    /**
     * Try to get the verse_1 based on split0, when failed, try to get it from the split1.
     */
    fun getVerse_1BasedOnScrolls(): Int {
        val split0verse_1 = lsSplit0.getVerse_1BasedOnScroll()
        if (split0verse_1 != 0) return split0verse_1

        val split1verse_1 = lsSplit1.getVerse_1BasedOnScroll()
        if (split1verse_1 != 0) return split1verse_1

        return 1 // default value for verse_1
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatchIfAvailable()
    }

    private fun disableNfcForegroundDispatchIfAvailable() {
        val _nfcAdapter = this.nfcAdapter

        if (_nfcAdapter != null) {
            try {
                _nfcAdapter.disableForegroundDispatch(this)
            } catch (e: IllegalStateException) {
                AppLog.e(TAG, "sometimes this happens.", e)
            }

        }
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatchIfAvailable()
    }

    private fun enableNfcForegroundDispatchIfAvailable() {
        nfcAdapter?.let { nfcAdapter ->
            val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, IsiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
            val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
            ndef.addDataType("application/vnd.yuku.alkitab.nfc.beam")
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, arrayOf(ndef), null)
        }
    }

    private fun tryGetIntentResultFromBeam(intent: Intent): IntentResult? {
        val action = intent.action
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null

        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        // only one message sent during the beam
        if (rawMsgs == null || rawMsgs.isEmpty()) return null

        val msg = rawMsgs[0] as NdefMessage
        // record 0 contains the MIME type, record 1 is the AAR, if present
        val records = msg.records
        if (records.isEmpty()) return null

        val json = String(records[0].payload)
        return try {
            val obj = JSONObject(json)
            val ari = obj.optInt("ari", -1)
            if (ari == -1) null else IntentResult(ari)

        } catch (e: JSONException) {
            AppLog.e(TAG, "Malformed json from nfc", e)
            null
        }
    }

    fun loadVersion(mv: MVersion) {
        try {
            val version = mv.version ?: throw RuntimeException() // caught below

            // we already have some other version loaded, so make the new version open the same book
            val bookId = this.activeBook.bookId
            val book = version.getBook(bookId)
            if (book != null) { // we load the new book succesfully
                this.activeBook = book
            } else { // too bad, this book was not found, get any book
                this.activeBook = version.firstBook
            }

            S.setActiveVersion(mv)
            displayActiveVersion()

            display(chapter_1, getVerse_1BasedOnScrolls(), false)

            App.getLbm().sendBroadcast(Intent(ACTION_ACTIVE_VERSION_CHANGED))

        } catch (e: Throwable) { // so we don't crash on the beginning of the app
            AppLog.e(TAG, "Error opening main version", e)

            MaterialDialog.Builder(this)
                .content(getString(R.string.version_error_opening, mv.longName))
                .positiveText(R.string.ok)
                .show()
        }
    }

    private fun displayActiveVersion() {
        bVersion.text = S.activeVersion().initials
        splitHandleButton.setLabel1("\u25b2 " + S.activeVersion().initials)
    }

    private fun loadSplitVersion(mv: MVersion): Boolean {
        try {
            val version = mv.version ?: throw RuntimeException() // caught below

            activeSplit = ActiveSplit(mv, version, mv.versionId)

            splitHandleButton.setLabel2(version.initials + " \u25bc")

            configureTextAppearancePanelForSplitVersion()

            return true
        } catch (e: Throwable) { // so we don't crash on the beginning of the app
            AppLog.e(TAG, "Error opening split version", e)

            MaterialDialog.Builder(this@IsiActivity)
                .content(getString(R.string.version_error_opening, mv.longName))
                .positiveText(R.string.ok)
                .show()

            return false
        }

    }

    private fun configureTextAppearancePanelForSplitVersion() {
        textAppearancePanel?.let { textAppearancePanel ->
            val activeSplit = activeSplit
            if (activeSplit == null) {
                textAppearancePanel.clearSplitVersion()
            } else {
                textAppearancePanel.setSplitVersion(activeSplit.versionId, activeSplit.version.longName)
            }
        }
    }

    private fun consumeKey(keyCode: Int): Boolean {
        // Handle dpad left/right, this always goes to prev/next chapter.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            bLeft_click()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            bRight_click()
            return true
        }

        // Handle volume up/down, the effect changes based on preferences.
        val pressResult = consumeUpDownKey(lsSplit0, keyCode)

        if (pressResult is VersesController.PressResult.Left) {
            bLeft_click()
            return true
        }

        if (pressResult is VersesController.PressResult.Right) {
            bRight_click()
            return true
        }

        if (pressResult is VersesController.PressResult.Consumed) {
            if (activeSplit != null) {
                lsSplit1.scrollToVerse(pressResult.targetVerse_1)
            }
            return true
        }

        return false
    }

    private fun consumeUpDownKey(versesController: VersesController, originalKeyCode: Int): VersesController.PressResult {
        var keyCode = originalKeyCode
        val volumeButtonsForNavigation = Preferences.getString(R.string.pref_volumeButtonNavigation_key, R.string.pref_volumeButtonNavigation_default)
        if (volumeButtonsForNavigation == "pasal" /* chapter */) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return VersesController.PressResult.Left
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return VersesController.PressResult.Right
            }
        } else if (volumeButtonsForNavigation == "ayat" /* verse */) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_DOWN
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_UP
        } else if (volumeButtonsForNavigation == "page") {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return versesController.pageDown()
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return versesController.pageUp()
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return versesController.verseDown()
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return versesController.verseUp()
        }

        return VersesController.PressResult.Nop
    }

    /**
     * Jump to a given verse reference in string format.
     *
     * @return ari of the parsed reference
     */
    private fun jumpTo(reference: String): Int {
        if (reference.trim().isEmpty()) {
            return 0
        }

        AppLog.d(TAG, "going to jump to $reference")

        val jumper = Jumper(reference)
        if (!jumper.parseSucceeded) {
            MaterialDialog.Builder(this)
                .content(R.string.alamat_tidak_sah_alamat, reference)
                .positiveText(R.string.ok)
                .show()
            return 0
        }

        val bookId = jumper.getBookId(S.activeVersion().consecutiveBooks)
        val selected = if (bookId != -1) {
            S.activeVersion().getBook(bookId) ?: this.activeBook // not avail, just fallback
        } else {
            this.activeBook
        }

        // set book
        this.activeBook = selected

        val chapter = jumper.chapter
        val verse = jumper.verse
        val ari_cv = if (chapter == -1 && verse == -1) {
            display(1, 1)
        } else {
            display(chapter, verse)
        }

        return Ari.encode(selected.bookId, ari_cv)
    }

    /**
     * Jump to a given ari
     */
    fun jumpToAri(ari: Int) {
        if (ari == 0) return

        val bookId = Ari.toBook(ari)
        val book = S.activeVersion().getBook(bookId)

        if (book == null) {
            AppLog.w(TAG, "bookId=$bookId not found for ari=$ari")
            return
        }

        this.activeBook = book
        val ari_cv = display(Ari.toChapter(ari), Ari.toVerse(ari))

        // call attention to the verse only if the displayed verse is equal to the requested verse
        if (ari == Ari.encode(this.activeBook.bookId, ari_cv)) {
            callAttentionForVerseToBothSplits(Ari.toVerse(ari))
        }
    }

    fun referenceFromSelectedVerses(selectedVerses: IntArrayList, book: Book): CharSequence {
        return when (selectedVerses.size()) {
            // should not be possible. So we don't do anything.
            0 -> book.reference(this.chapter_1)
            1 -> book.reference(this.chapter_1, selectedVerses.get(0))
            else -> book.reference(this.chapter_1, selectedVerses)
        }
    }

    /**
     * Construct text for copying or sharing (in plain text).
     *
     * @param isSplitVersion whether take the verse text from the main or from the split version.
     * @return [0] text for copying/sharing, [1] text to be submitted to the share url service
     */
    fun prepareTextForCopyShare(selectedVerses_1: IntArrayList, reference: CharSequence, isSplitVersion: Boolean): Array<String> {
        val res0 = StringBuilder()
        val res1 = StringBuilder()

        res0.append(reference)

        if (Preferences.getBoolean(getString(R.string.pref_copyWithVersionName_key), resources.getBoolean(R.bool.pref_copyWithVersionName_default))) {
            // Fallback to primary version for safety
            val version = if (isSplitVersion) activeSplit?.version ?: S.activeVersion() else S.activeVersion()
            val versionShortName = version.shortName
            if (versionShortName != null) {
                res0.append(" (").append(versionShortName).append(")")
            }
        }

        if (Preferences.getBoolean(getString(R.string.pref_copyWithVerseNumbers_key), false) && selectedVerses_1.size() > 1) {
            res0.append('\n')

            // append each selected verse with verse number prepended
            var i = 0
            val len = selectedVerses_1.size()
            while (i < len) {
                val verse_1 = selectedVerses_1.get(i)
                val verseText = if (isSplitVersion) dataSplit1.getVerseText(verse_1) else dataSplit0.getVerseText(verse_1)

                if (verseText != null) {
                    val verseTextPlain = FormattedVerseText.removeSpecialCodes(verseText)

                    res0.append(verse_1)
                    res1.append(verse_1)
                    res0.append(' ')
                    res1.append(' ')

                    res0.append(verseTextPlain)
                    res1.append(verseText)

                    if (i != len - 1) {
                        res0.append('\n')
                        res1.append('\n')
                    }
                }
                i++
            }
        } else {
            res0.append("  ")

            // append each selected verse without verse number prepended
            for (i in 0 until selectedVerses_1.size()) {
                val verse_1 = selectedVerses_1.get(i)
                val verseText = if (isSplitVersion) dataSplit1.getVerseText(verse_1) else dataSplit0.getVerseText(verse_1)

                if (verseText != null) {
                    val verseTextPlain = FormattedVerseText.removeSpecialCodes(verseText)

                    if (i != 0) {
                        res0.append('\n')
                        res1.append('\n')
                    }
                    res0.append(verseTextPlain)
                    res1.append(verseText)
                }
            }
        }

        return arrayOf(res0.toString(), res1.toString())
    }

    fun applyPreferences() {
        // make sure S applied variables are set first
        S.recalculateAppliedValuesBasedOnPreferences()

        // apply background color, and clear window background to prevent overdraw
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val backgroundColor = S.applied().backgroundColor
        root.setBackgroundColor(backgroundColor)

        // scrollbar must be visible!
        val thumb = if (ColorUtils.calculateLuminance(backgroundColor) > 0.5) {
            ActivityCompat.getDrawable(this, R.drawable.scrollbar_handle_material_for_light)
        } else {
            ActivityCompat.getDrawable(this, R.drawable.scrollbar_handle_material_for_dark)
        }

        if (thumb != null) {
            lsSplit0.setViewScrollbarThumb(thumb)
            lsSplit1.setViewScrollbarThumb(thumb)
        }

        // necessary
        uiSplit0 = uiSplit0.copy(textSizeMult = calculateTextSizeMult(S.activeVersionId()))
        uiSplit1 = uiSplit1.copy(textSizeMult = calculateTextSizeMult(activeSplit?.versionId))

        lsSplit0.setViewPadding(SettingsActivity.getPaddingBasedOnPreferences())
        lsSplit1.setViewPadding(SettingsActivity.getPaddingBasedOnPreferences())
    }

    override fun onStop() {
        super.onStop()

        Preferences.hold()
        try {
            Preferences.setInt(Prefkey.lastBookId, this.activeBook.bookId)
            Preferences.setInt(Prefkey.lastChapter, chapter_1)
            Preferences.setInt(Prefkey.lastVerse, getVerse_1BasedOnScrolls())
            Preferences.setString(Prefkey.lastVersionId, S.activeVersionId())
            val activeSplit = activeSplit
            if (activeSplit == null) {
                Preferences.remove(Prefkey.lastSplitVersionId)
            } else {
                Preferences.setString(Prefkey.lastSplitVersionId, activeSplit.versionId)
                Preferences.setString(Prefkey.lastSplitOrientation, splitHandleButton.orientation.name)
            }
        } finally {
            Preferences.unhold()
        }

        history.save()
    }

    override fun onStart() {
        super.onStart()

        applyPreferences()

        window.decorView.keepScreenOn = Preferences.getBoolean(R.string.pref_keepScreenOn_key, R.bool.pref_keepScreenOn_default)

        if (needsRestart) {
            needsRestart = false
            recreate()
        }
    }

    override fun onBackPressed() {
        val debug = Preferences.getBoolean("secret_debug_back_button", false)

        if (debug) Toast.makeText(this, "@@onBackPressed TAP=" + (textAppearancePanel != null) + " fullScreen=" + fullScreen, Toast.LENGTH_SHORT).show()

        when {
            textAppearancePanel != null -> {
                if (debug) Toast.makeText(this, "inside textAppearancePanel != null", Toast.LENGTH_SHORT).show()
                textAppearancePanel?.hide()
                textAppearancePanel = null
            }
            fullScreen -> {
                if (debug) Toast.makeText(this, "inside fullScreen == true", Toast.LENGTH_SHORT).show()
                setFullScreen(false)
                leftDrawer.handle.setFullScreen(false)
            }
            else -> {
                if (debug) Toast.makeText(this, "will call super", Toast.LENGTH_SHORT).show()
                super.onBackPressed()
            }
        }
    }

    private fun bGoto_click() {
        Tracker.trackEvent("nav_goto_button_click")

        val r = {
            startActivityForResult(GotoActivity.createIntent(this.activeBook.bookId, this.chapter_1, getVerse_1BasedOnScrolls()), RequestCodes.FromActivity.Goto)
        }

        if (!Preferences.getBoolean(Prefkey.history_button_understood, false) && history.size > 0) {
            MaterialDialog.Builder(this)
                .content(R.string.goto_button_history_tip)
                .positiveText(R.string.ok)
                .onPositive { _, _ ->
                    Preferences.setBoolean(Prefkey.history_button_understood, true)
                    r()
                }
                .show()
        } else {
            r()
        }
    }

    private fun bGoto_longClick() {
        Tracker.trackEvent("nav_goto_button_long_click")
        if (history.size > 0) {
            MaterialDialogAdapterHelper.show(MaterialDialog.Builder(this), HistoryAdapter())
            Preferences.setBoolean(Prefkey.history_button_understood, true)
        } else {
            Snackbar.make(root, R.string.recentverses_not_available, Snackbar.LENGTH_SHORT).show()
        }
    }

    class HistoryEntryHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text1: TextView = itemView.findViewById(android.R.id.text1)
    }

    inner class HistoryAdapter : MaterialDialogAdapterHelper.Adapter() {
        private val timeFormat = DateFormat.getTimeFormat(App.context)
        private val mediumDateFormat = DateFormat.getMediumDateFormat(App.context)

        private val thisCreatorId = InstallationUtil.getInstallationId()
        private var defaultTextColor = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
            val textView = view as TextView
            defaultTextColor = textView.currentTextColor
            return HistoryEntryHolder(view)
        }

        override fun onBindViewHolder(_holder_: RecyclerView.ViewHolder, position: Int) {
            val holder = _holder_ as HistoryEntryHolder

            run {
                val ari = history.getAri(position)
                val sb = SpannableStringBuilder()
                sb.append(S.activeVersion().reference(ari))
                sb.append("  ")
                val sb_len = sb.length
                sb.append(formatTimestamp(history.getTimestamp(position)))
                sb.setSpan(ForegroundColorSpan(-0x555556), sb_len, sb.length, 0)
                sb.setSpan(RelativeSizeSpan(0.7f), sb_len, sb.length, 0)

                holder.text1.text = sb

                if (thisCreatorId == history.getCreatorId(position)) {
                    holder.text1.setTextColor(defaultTextColor)
                } else {
                    holder.text1.setTextColor(ResourcesCompat.getColor(resources, R.color.escape, theme))
                }
            }

            holder.itemView.setOnClickListener {
                dismissDialog()

                val which = holder.adapterPosition

                val ari = history.getAri(which)
                jumpToAri(ari)
                history.add(ari)
            }
        }

        private fun formatTimestamp(timestamp: Long): CharSequence {
            run {
                val now = System.currentTimeMillis()
                val delta = now - timestamp
                if (delta <= 200000) {
                    return getString(R.string.recentverses_just_now)
                } else if (delta <= 3600000) {
                    return getString(R.string.recentverses_min_plural_ago, (delta / 60000.0).roundToLong().toString())
                }
            }

            run {
                val now = GregorianCalendar.getInstance()
                val that = GregorianCalendar.getInstance()
                that.timeInMillis = timestamp
                if (now.get(Calendar.YEAR) == that.get(Calendar.YEAR)) {
                    if (now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)) {
                        return getString(R.string.recentverses_today_time, timeFormat.format(that.time))
                    } else if (now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR) + 1) {
                        return getString(R.string.recentverses_yesterday_time, timeFormat.format(that.time))
                    }
                }

                return mediumDateFormat.format(that.time)
            }
        }

        override fun getItemCount(): Int {
            return history.size
        }
    }

    private fun buildMenu(menu: Menu) {
        menu.clear()
        menuInflater.inflate(R.menu.activity_isi, menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        buildMenu(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu != null) {
            buildMenu(menu)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                leftDrawer.toggleDrawer()
                return true
            }
            R.id.menuSearch -> {
                Tracker.trackEvent("nav_search_click")
                menuSearch_click()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    @TargetApi(19)
    fun setFullScreen(yes: Boolean) {
        if (fullScreen == yes) return  // no change

        val decorView = window.decorView

        if (yes) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.hide()

            if (Build.VERSION.SDK_INT >= 19) {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            supportActionBar?.show()

            if (Build.VERSION.SDK_INT >= 19) {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        fullScreen = yes

        updateToolbarLocation()
    }

    private fun updateToolbarLocation() {
        // 3 kinds of possible layout:
        // - fullscreen
        // - not fullscreen, toolbar at bottom
        // - not fullscreen, toolbar at top

        // root contains exactly 2 children: toolbar and splitRoot.
        // Need to move toolbar and splitRoot in order to accomplish this.

        if (!fullScreen) {
            root.removeView(toolbar)
            root.removeView(splitRoot)

            if (Preferences.getBoolean(R.string.pref_bottomToolbarOnText_key, R.bool.pref_bottomToolbarOnText_default)) {
                root.addView(splitRoot)
                root.addView(toolbar)
            } else {
                root.addView(toolbar)
                root.addView(splitRoot)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && fullScreen) {
            if (Build.VERSION.SDK_INT >= 19) {
                val decorView = window.decorView
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE
            }
        }
    }

    private fun setShowTextAppearancePanel(yes: Boolean) {
        if (!yes) {
            textAppearancePanel?.hide()
            textAppearancePanel = null
            return
        }

        if (textAppearancePanel == null) { // not showing yet
            textAppearancePanel = TextAppearancePanel(
                this,
                overlayContainer,
                object : TextAppearancePanel.Listener {
                    override fun onValueChanged() {
                        applyPreferences()
                    }

                    override fun onCloseButtonClick() {
                        textAppearancePanel?.hide()
                        textAppearancePanel = null
                    }
                },
                RequestCodes.FromActivity.TextAppearanceGetFonts,
                RequestCodes.FromActivity.TextAppearanceCustomColors
            )
            configureTextAppearancePanelForSplitVersion()
            textAppearancePanel?.show()
        }
    }

    private fun setNightMode(yes: Boolean) {
        val previousValue = Preferences.getBoolean(Prefkey.is_night_mode, false)
        if (previousValue == yes) return

        Preferences.setBoolean(Prefkey.is_night_mode, yes)

        applyPreferences()
        applyActionBarAndStatusBarColors()

        textAppearancePanel?.displayValues()

        App.getLbm().sendBroadcast(Intent(ACTION_NIGHT_MODE_CHANGED))
    }

    private fun openVersionsDialog() {
        // If there is no db versions, immediately open manage version screen.
        if (S.getDb().listAllVersions().isEmpty()) {
            startActivity(VersionsActivity.createIntent())
            return
        }

        S.openVersionsDialog(this, false, S.activeVersionId()) { mv ->
            trackVersionSelect(mv, false)
            loadVersion(mv)
        }
    }

    private fun openSplitVersionsDialog() {
        S.openVersionsDialog(this, true, activeSplit?.versionId) { mv ->
            if (mv == null) { // closing split version
                disableSplitVersion()
            } else {
                trackVersionSelect(mv, true)
                val ok = loadSplitVersion(mv)
                if (ok) {
                    openSplitDisplay()
                    displaySplitFollowingMaster(getVerse_1BasedOnScrolls())
                } else {
                    disableSplitVersion()
                }
            }
        }
    }

    private fun trackVersionSelect(mv: MVersion?, isSplit: Boolean) {
        if (mv is MVersionDb) {
            val preset_name = mv.preset_name
            Tracker.trackEvent("versions_dialog_select", "is_split", isSplit, FirebaseAnalytics.Param.ITEM_NAME, preset_name ?: "no_preset_name")
        } else if (mv is MVersionInternal) {
            Tracker.trackEvent("versions_dialog_select", "is_split", isSplit, FirebaseAnalytics.Param.ITEM_NAME, "internal")
        }
    }

    private fun disableSplitVersion() {
        activeSplit = null
        closeSplitDisplay()

        configureTextAppearancePanelForSplitVersion()
    }

    private fun openSplitDisplay() {
        if (splitHandleButton.visibility == View.VISIBLE) {
            return  // it's already split, no need to do anything
        }

        configureSplitSizes()

        bVersion.visibility = View.GONE
        actionMode?.invalidate()
        leftDrawer.handle.setSplitVersion(true)
    }

    fun configureSplitSizes() {
        splitHandleButton.visibility = View.VISIBLE

        var prop = Preferences.getFloat(Prefkey.lastSplitProp, Float.MIN_VALUE)
        if (prop == Float.MIN_VALUE || prop < 0f || prop > 1f) {
            prop = 0.5f // guard against invalid values
        }

        val splitHandleThickness = resources.getDimensionPixelSize(R.dimen.split_handle_thickness)
        if (splitHandleButton.orientation == SplitHandleButton.Orientation.vertical) {
            splitRoot.orientation = LinearLayout.VERTICAL

            val totalHeight = splitRoot.height
            val masterHeight = ((totalHeight - splitHandleThickness) * prop).toInt()

            run {
                // divide the screen space
                lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, masterHeight)
            }

            // no need to set height, because it has been set to match_parent, so it takes the remaining space.
            lsSplit1.setViewVisibility(View.VISIBLE)

            run {
                val lp = splitHandleButton.layoutParams
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = splitHandleThickness
                splitHandleButton.layoutParams = lp
            }
        } else {
            splitRoot.orientation = LinearLayout.HORIZONTAL

            val totalWidth = splitRoot.width
            val masterWidth = ((totalWidth - splitHandleThickness) * prop).toInt()

            run {
                // divide the screen space
                lsSplit0.setViewLayoutSize(masterWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            }

            // no need to set width, because it has been set to match_parent, so it takes the remaining space.
            lsSplit1.setViewVisibility(View.VISIBLE)

            run {
                val lp = splitHandleButton.layoutParams
                lp.width = splitHandleThickness
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                splitHandleButton.layoutParams = lp
            }
        }
    }

    private fun closeSplitDisplay() {
        if (splitHandleButton.visibility == View.GONE) {
            return  // it's already not split, no need to do anything
        }

        splitHandleButton.visibility = View.GONE
        lsSplit1.setViewVisibility(View.GONE)

        run { lsSplit0.setViewLayoutSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }

        bVersion.visibility = View.VISIBLE
        actionMode?.invalidate()
        leftDrawer.handle.setSplitVersion(false)
    }

    private fun menuSearch_click() {
        startActivity(SearchActivity.createIntent(this.activeBook.bookId))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RequestCodes.FromActivity.Goto && resultCode == Activity.RESULT_OK && data != null) {
            val result = GotoActivity.obtainResult(data)
            if (result != null) {
                val ari_cv: Int

                if (result.bookId == -1) {
                    // stay on the same book
                    ari_cv = display(result.chapter_1, result.verse_1)

                    // call attention to the verse only if the displayed verse is equal to the requested verse
                    if (Ari.encode(0, result.chapter_1, result.verse_1) == ari_cv) {
                        callAttentionForVerseToBothSplits(result.verse_1)
                    }
                } else {
                    // change book
                    val book = S.activeVersion().getBook(result.bookId)
                    if (book != null) {
                        this.activeBook = book
                    } else { // no book, just chapter and verse.
                        result.bookId = this.activeBook.bookId
                    }

                    ari_cv = display(result.chapter_1, result.verse_1)

                    // select the verse only if the displayed verse is equal to the requested verse
                    if (Ari.encode(result.bookId, result.chapter_1, result.verse_1) == Ari.encode(this.activeBook.bookId, ari_cv)) {
                        callAttentionForVerseToBothSplits(result.verse_1)
                    }
                }

                if (result.verse_1 == 0 && Ari.toVerse(ari_cv) == 1) {
                    // verse 0 requested, but display method causes it to show verse_1 1.
                    // However we want to store verse_1 0 on the history.
                    history.add(Ari.encode(this.activeBook.bookId, Ari.toChapter(ari_cv), 0))
                } else {
                    history.add(Ari.encode(this.activeBook.bookId, ari_cv))
                }
            }
        } else if (requestCode == RequestCodes.FromActivity.Share && resultCode == Activity.RESULT_OK) {
            val result = ShareActivity.obtainResult(data)
            val chosenIntent = result?.chosenIntent
            val packageName = chosenIntent?.component?.packageName
            if (packageName != null) {
                if (packageName == "com.facebook.katana") {
                    val verseUrl = chosenIntent.getStringExtra(EXTRA_verseUrl)
                    if (verseUrl != null) {
                        chosenIntent.putExtra(Intent.EXTRA_TEXT, verseUrl) // change text to url
                    }
                } else if (packageName == "com.whatsapp") {
                    chosenIntent.removeExtra(Intent.EXTRA_SUBJECT)
                }
                startActivity(chosenIntent)
            }
        } else if (requestCode == RequestCodes.FromActivity.TextAppearanceGetFonts) {
            textAppearancePanel?.onActivityResult(requestCode)
        } else if (requestCode == RequestCodes.FromActivity.TextAppearanceCustomColors) {
            textAppearancePanel?.onActivityResult(requestCode)
        } else if (requestCode == RequestCodes.FromActivity.EditNote1 && resultCode == Activity.RESULT_OK) {
            reloadBothAttributeMaps()
        } else if (requestCode == RequestCodes.FromActivity.EditNote2 && resultCode == Activity.RESULT_OK) {
            lsSplit0.uncheckAllVerses(true)
            reloadBothAttributeMaps()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Display specified chapter and verse of the active book. By default, all checked verses will be unchecked.
     *
     * @param uncheckAllVerses whether we want to always make all verses unchecked after this operation.
     * @return Ari that contains only chapter and verse. Book always set to 0.
     */
    @JvmOverloads
    fun display(chapter_1: Int, verse_1: Int, uncheckAllVerses: Boolean = true): Int {
        val current_chapter_1 = this.chapter_1

        val available_chapter_1 = chapter_1.coerceIn(1, this.activeBook.chapter_count)
        val available_verse_1 = verse_1.coerceIn(1, this.activeBook.verse_counts[available_chapter_1 - 1])

        run {
            // main
            this.uncheckVersesWhenActionModeDestroyed = false
            try {
                val ok = loadChapterToVersesController(contentResolver, lsSplit0, { dataSplit0 = it }, S.activeVersion(), S.activeVersionId(), this.activeBook, available_chapter_1, current_chapter_1, uncheckAllVerses)
                if (!ok) return 0
            } finally {
                this.uncheckVersesWhenActionModeDestroyed = true
            }

            // tell activity
            this.chapter_1 = available_chapter_1

            lsSplit0.scrollToVerse(available_verse_1)
        }

        displaySplitFollowingMaster(available_verse_1)

        // set goto button text
        val reference = this.activeBook.reference(available_chapter_1)
        bGoto.text = reference.replace(' ', '\u00a0')

        if (fullScreen) {
            Toast.makeText(this, reference, Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 0)
            }.show()
        }

        if (dictionaryMode) {
            finishDictionaryMode()
        }

        return Ari.encode(0, available_chapter_1, available_verse_1)
    }

    private fun loadChapterToVersesController(
        cr: ContentResolver,
        versesController: VersesController,
        dataSetter: (VersesDataModel) -> Unit,
        version: Version,
        versionId: String,
        book: Book,
        chapter_1: Int,
        current_chapter_1: Int,
        uncheckAllVerses: Boolean
    ): Boolean {
        val verses = version.loadChapterText(book, chapter_1) ?: return false

        //# max is set to 30 (one chapter has a max of 30 blocks. Already almost impossible)
        val max = 30
        val tmp_pericope_aris = IntArray(max)
        val tmp_pericope_blocks = arrayOfNulls<PericopeBlock>(max)
        val nblock = version.loadPericope(book.bookId, chapter_1, tmp_pericope_aris, tmp_pericope_blocks, max)
        val pericope_aris = tmp_pericope_aris.copyOf(nblock)
        val pericope_blocks = tmp_pericope_blocks.copyOf(nblock).map { block -> block!! }.toTypedArray()

        val retainSelectedVerses = !uncheckAllVerses && chapter_1 == current_chapter_1
        setDataWithRetainSelectedVerses(cr, versesController, dataSetter, retainSelectedVerses, Ari.encode(book.bookId, chapter_1, 0), pericope_aris, pericope_blocks, nblock, verses, version, versionId)

        return true
    }

    // Moved from the old VersesView method
    private fun setDataWithRetainSelectedVerses(
        cr: ContentResolver,
        versesController: VersesController,
        dataSetter: (VersesDataModel) -> Unit,
        retainSelectedVerses: Boolean,
        ariBc: Int,
        pericope_aris: IntArray,
        pericope_blocks: Array<PericopeBlock>,
        nblock: Int,
        verses: SingleChapterVerses,
        version: Version,
        versionId: String
    ) {
        var selectedVerses_1: IntArrayList? = null
        if (retainSelectedVerses) {
            selectedVerses_1 = versesController.getCheckedVerses_1()
        }

        //# fill adapter with new data. make sure all checked states are reset
        versesController.uncheckAllVerses(true)

        val versesAttributes = VerseAttributeLoader.load(S.getDb(), cr, ariBc, verses)

        val newData = VersesDataModel(ariBc, verses, nblock, pericope_aris, pericope_blocks, version, versionId, versesAttributes)
        dataSetter(newData)

        if (selectedVerses_1 != null) {
            versesController.checkVerses(selectedVerses_1, true)
        }
    }

    private fun displaySplitFollowingMaster(verse_1: Int) {
        val activeSplit = activeSplit
        if (activeSplit != null) { // split1
            val splitBook = activeSplit.version.getBook(this.activeBook.bookId)
            if (splitBook == null) {
                lsSplit1.setEmptyMessage(getString(R.string.split_version_cant_display_verse, this.activeBook.reference(this.chapter_1), activeSplit.version.shortName), S.applied().fontColor)
                dataSplit1 = VersesDataModel.EMPTY
            } else {
                lsSplit1.setEmptyMessage(null, S.applied().fontColor)
                this.uncheckVersesWhenActionModeDestroyed = false
                try {
                    loadChapterToVersesController(contentResolver, lsSplit1, { dataSplit1 = it }, activeSplit.version, activeSplit.versionId, splitBook, this.chapter_1, this.chapter_1, true)
                } finally {
                    this.uncheckVersesWhenActionModeDestroyed = true
                }
                lsSplit1.scrollToVerse(verse_1)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return consumeKey(keyCode) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        return consumeKey(keyCode) || super.onKeyMultiple(keyCode, repeatCount, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val volumeButtonsForNavigation = Preferences.getString(R.string.pref_volumeButtonNavigation_key, R.string.pref_volumeButtonNavigation_default)
        if (volumeButtonsForNavigation != "default") { // consume here
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun getLeftDrawer(): LeftDrawer {
        return leftDrawer
    }

    fun bLeft_click() {
        Tracker.trackEvent("nav_left_click")
        val currentBook = this.activeBook
        if (chapter_1 == 1) {
            // we are in the beginning of the book, so go to prev book
            var tryBookId = currentBook.bookId - 1
            while (tryBookId >= 0) {
                val newBook = S.activeVersion().getBook(tryBookId)
                if (newBook != null) {
                    this.activeBook = newBook
                    val newChapter_1 = newBook.chapter_count // to the last chapter
                    display(newChapter_1, 1)
                    break
                }
                tryBookId--
            }
            // whileelse: now is already Genesis 1. No need to do anything
        } else {
            val newChapter = chapter_1 - 1
            display(newChapter, 1)
        }
    }

    fun bRight_click() {
        Tracker.trackEvent("nav_right_click")
        val currentBook = this.activeBook
        if (chapter_1 >= currentBook.chapter_count) {
            val maxBookId = S.activeVersion().maxBookIdPlusOne
            var tryBookId = currentBook.bookId + 1
            while (tryBookId < maxBookId) {
                val newBook = S.activeVersion().getBook(tryBookId)
                if (newBook != null) {
                    this.activeBook = newBook
                    display(1, 1)
                    break
                }
                tryBookId++
            }
            // whileelse: now is already Revelation (or the last book) at the last chapter. No need to do anything
        } else {
            val newChapter = chapter_1 + 1
            display(newChapter, 1)
        }
    }

    private fun bVersion_click() {
        Tracker.trackEvent("nav_version_click")
        openVersionsDialog()
    }

    override fun onSearchRequested(): Boolean {
        menuSearch_click()

        return true
    }

    inner class AttributeListener : VersesController.AttributeListener() {
        fun openBookmarkDialog(_id: Long) {
            val dialog = TypeBookmarkDialog.EditExisting(this@IsiActivity, _id)
            dialog.setListener { reloadBothAttributeMaps() }
            dialog.show()
        }

        override fun onBookmarkAttributeClick(version: Version, versionId: String, ari: Int) {
            val markers = S.getDb().listMarkersForAriKind(ari, Marker.Kind.bookmark)
            if (markers.size == 1) {
                openBookmarkDialog(markers[0]._id)
            } else {
                MaterialDialogAdapterHelper.show(MaterialDialog.Builder(this@IsiActivity).title(R.string.edit_bookmark), MultipleMarkerSelectAdapter(version, versionId, markers, Marker.Kind.bookmark))
            }
        }

        fun openNoteDialog(_id: Long) {
            startActivityForResult(NoteActivity.createEditExistingIntent(_id), RequestCodes.FromActivity.EditNote1)
        }

        override fun onNoteAttributeClick(version: Version, versionId: String, ari: Int) {
            val markers = S.getDb().listMarkersForAriKind(ari, Marker.Kind.note)
            if (markers.size == 1) {
                openNoteDialog(markers[0]._id)
            } else {
                MaterialDialogAdapterHelper.show(MaterialDialog.Builder(this@IsiActivity).title(R.string.edit_note), MultipleMarkerSelectAdapter(version, versionId, markers, Marker.Kind.note))
            }
        }

        inner class MarkerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val lDate: TextView = itemView.findViewById(R.id.lDate)
            val lCaption: TextView = itemView.findViewById(R.id.lCaption)
            val lSnippet: TextView = itemView.findViewById(R.id.lSnippet)
            val panelLabels: FlowLayout = itemView.findViewById(R.id.panelLabels)
        }

        inner class MultipleMarkerSelectAdapter(val version: Version, versionId: String, private val markers: List<Marker>, val kind: Marker.Kind) : MaterialDialogAdapterHelper.Adapter() {
            val textSizeMult = S.getDb().getPerVersionSettings(versionId).fontSizeMultiplier

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return MarkerHolder(layoutInflater.inflate(R.layout.item_marker, parent, false))
            }

            override fun onBindViewHolder(_holder_: RecyclerView.ViewHolder, position: Int) {
                val holder = _holder_ as MarkerHolder

                run {
                    val marker = markers[position]

                    run {
                        val addTime = marker.createTime
                        val modifyTime = marker.modifyTime

                        if (addTime == modifyTime) {
                            holder.lDate.text = Sqlitil.toLocaleDateMedium(addTime)
                        } else {
                            holder.lDate.text = getString(R.string.create_edited_modified_time, Sqlitil.toLocaleDateMedium(addTime), Sqlitil.toLocaleDateMedium(modifyTime))
                        }

                        Appearances.applyMarkerDateTextAppearance(holder.lDate, textSizeMult)
                    }

                    val ari = marker.ari
                    val reference = version.reference(ari)
                    val caption = marker.caption

                    if (kind == Marker.Kind.bookmark) {
                        holder.lCaption.text = caption
                        Appearances.applyMarkerTitleTextAppearance(holder.lCaption, textSizeMult)

                        holder.lSnippet.visibility = View.GONE

                        val labels = S.getDb().listLabelsByMarker(marker)
                        if (labels.size != 0) {
                            holder.panelLabels.visibility = View.VISIBLE
                            holder.panelLabels.removeAllViews()
                            for (label in labels) {
                                holder.panelLabels.addView(MarkerListActivity.getLabelView(layoutInflater, holder.panelLabels, label))
                            }
                        } else {
                            holder.panelLabels.visibility = View.GONE
                        }

                    } else if (kind == Marker.Kind.note) {
                        holder.lCaption.text = reference
                        Appearances.applyMarkerTitleTextAppearance(holder.lCaption, textSizeMult)
                        holder.lSnippet.text = caption
                        Appearances.applyTextAppearance(holder.lSnippet, textSizeMult)
                    }

                    holder.itemView.setBackgroundColor(S.applied().backgroundColor)
                }

                holder.itemView.setOnClickListener {
                    dismissDialog()

                    val which = holder.adapterPosition
                    val marker = markers[which]
                    if (kind == Marker.Kind.bookmark) {
                        openBookmarkDialog(marker._id)
                    } else if (kind == Marker.Kind.note) {
                        openNoteDialog(marker._id)
                    }
                }
            }

            override fun getItemCount(): Int {
                return markers.size
            }
        }

        override fun onProgressMarkAttributeClick(version: Version, versionId: String, preset_id: Int) {
            S.getDb().getProgressMarkByPresetId(preset_id)?.let { progressMark ->
                ProgressMarkRenameDialog.show(this@IsiActivity, progressMark, object : ProgressMarkRenameDialog.Listener {
                    override fun onOked() {
                        lsSplit0.uncheckAllVerses(true)
                    }

                    override fun onDeleted() {
                        lsSplit0.uncheckAllVerses(true)
                    }
                })
            }
        }

        override fun onHasMapsAttributeClick(version: Version, versionId: String, ari: Int) {
            val locale = version.locale

            try {
                val intent = Intent("palki.maps.action.SHOW_MAPS_DIALOG")
                intent.putExtra("ari", ari)

                if (locale != null) {
                    intent.putExtra("locale", locale)
                }

                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                MaterialDialog.Builder(this@IsiActivity)
                    .content(R.string.maps_could_not_open)
                    .positiveText(R.string.ok)
                    .show()
            }

        }
    }

    inner class VerseInlineLinkSpanFactory(private val sourceSupplier: () -> VersesController) : VerseInlineLinkSpan.Factory {

        override fun create(type: VerseInlineLinkSpan.Type, arif: Int) = object : VerseInlineLinkSpan(type, arif) {
            override fun onClick(type: Type, arif: Int) {
                val source = sourceSupplier()
                val activeSplit = activeSplit
                if (type == Type.xref) {
                    val dialog = XrefDialog.newInstance(arif)

                    val verseSelectedListener = { arif_source: Int, ari_target: Int ->
                        val ari_source = arif_source ushr 8

                        dialog.dismiss()
                        jumpToAri(ari_target)

                        // add both xref source and target, so user can go back to source easily
                        history.add(ari_source)
                        history.add(ari_target)
                    }

                    if (source === lsSplit0 || activeSplit == null) { // use activeVersion
                        dialog.init(S.activeVersion(), S.activeVersionId(), verseSelectedListener)
                    } else if (source === lsSplit1) { // use activeSplitVersion
                        dialog.init(activeSplit.version, activeSplit.versionId, verseSelectedListener)
                    }

                    val fm = supportFragmentManager
                    dialog.show(fm, "XrefDialog")
                } else if (type == Type.footnote) {
                    val fe = when {
                        source === lsSplit0 -> S.activeVersion().getFootnoteEntry(arif)
                        source === lsSplit1 -> activeSplit?.version?.getFootnoteEntry(arif)
                        else -> null
                    }

                    if (fe != null) {
                        val footnoteText = SpannableStringBuilder()
                        VerseRenderer.appendSuperscriptNumber(footnoteText, arif and 0xff)
                        footnoteText.append(" ")

                        MaterialDialog.Builder(this@IsiActivity)
                            .content(FormattedTextRenderer.render(fe.content, footnoteText))
                            .positiveText(R.string.ok)
                            .show()
                    } else {
                        MaterialDialog.Builder(this@IsiActivity)
                            .content(String.format(Locale.US, "Error: footnote arif 0x%08x couldn't be loaded", arif))
                            .positiveText(R.string.ok)
                            .show()
                    }
                } else {
                    MaterialDialog.Builder(this@IsiActivity)
                        .content("Error: Unknown inline link type: $type")
                        .positiveText(R.string.ok)
                        .show()
                }
            }
        }
    }

    enum class RibkaEligibility {
        None,
        Main,
        Split,
    }

    /**
     * Check whether we are using a version eligible for ribka.
     */
    fun checkRibkaEligibility(): RibkaEligibility {
        val validPresetName = "in-ayt"

        val activeMVersion = S.activeMVersion()
        val activePresetName = if (activeMVersion is MVersionDb) activeMVersion.preset_name else null

        if (validPresetName == activePresetName) {
            return RibkaEligibility.Main
        }

        val splitPresetName = (activeSplit?.mv as? MVersionDb)?.preset_name

        return if (validPresetName == splitPresetName) RibkaEligibility.Split else RibkaEligibility.None
    }

    fun reloadBothAttributeMaps() {
        val newDataSplit0 = reloadAttributeMapsToVerseDataModel(dataSplit0)
        dataSplit0 = newDataSplit0

        if (activeSplit != null) {
            val newDataSplit1 = reloadAttributeMapsToVerseDataModel(dataSplit1)
            dataSplit1 = newDataSplit1
        }
    }

    private fun reloadAttributeMapsToVerseDataModel(
        data: VersesDataModel
    ): VersesDataModel {
        val versesAttributes = VerseAttributeLoader.load(
            S.getDb(),
            contentResolver,
            data.ari_bc_,
            data.verses_
        )

        return data.copy(versesAttributes = versesAttributes)
    }

    /**
     * @param aris aris where the verses are to be checked for dictionary words.
     */
    private fun startDictionaryMode(aris: Set<Int>) {
        if (!OtherAppIntegration.hasIntegratedDictionaryApp()) {
            OtherAppIntegration.askToInstallDictionary(this)
            return
        }

        dictionaryMode = true

        uiSplit0 = uiSplit0.copy(dictionaryModeAris = aris)
        uiSplit1 = uiSplit1.copy(dictionaryModeAris = aris)
    }

    private fun finishDictionaryMode() {
        dictionaryMode = false

        uiSplit0 = uiSplit0.copy(dictionaryModeAris = emptySet())
        uiSplit1 = uiSplit1.copy(dictionaryModeAris = emptySet())
    }

    override fun bMarkers_click() {
        startActivity(MarkersActivity.createIntent())
    }

    override fun bDisplay_click() {
        Tracker.trackEvent("left_drawer_display_click")
        setShowTextAppearancePanel(textAppearancePanel == null)
    }

    override fun cFullScreen_checkedChange(isChecked: Boolean) {
        Tracker.trackEvent("left_drawer_full_screen_click")
        setFullScreen(isChecked)
    }

    override fun cNightMode_checkedChange(isChecked: Boolean) {
        Tracker.trackEvent("left_drawer_night_mode_click")
        setNightMode(isChecked)
    }

    override fun cSplitVersion_checkedChange(cSplitVersion: SwitchCompat, isChecked: Boolean) {
        Tracker.trackEvent("left_drawer_split_click")
        if (isChecked) {
            cSplitVersion.isChecked = false // do it later, at the version chooser dialog
            openSplitVersionsDialog()
        } else {
            disableSplitVersion()
        }
    }

    override fun bProgressMarkList_click() {
        Tracker.trackEvent("left_drawer_progress_mark_list_click")
        if (S.getDb().countAllProgressMarks() > 0) {
            val dialog = ProgressMarkListDialog()
            dialog.progressMarkSelectedListener = { preset_id ->
                gotoProgressMark(preset_id)
            }
            dialog.show(supportFragmentManager, "dialog_progress_mark_list")
            leftDrawer.closeDrawer()
        } else {
            MaterialDialog.Builder(this)
                .content(R.string.pm_activate_tutorial)
                .positiveText(R.string.ok)
                .show()
        }
    }

    override fun bProgress_click(preset_id: Int) {
        gotoProgressMark(preset_id)
    }

    override fun bCurrentReadingClose_click() {
        Tracker.trackEvent("left_drawer_current_reading_close_click")

        CurrentReading.clear()
    }

    override fun bCurrentReadingReference_click() {
        Tracker.trackEvent("left_drawer_current_reading_verse_reference_click")

        val aris = CurrentReading.get() ?: return

        val ari_start = aris[0]
        jumpToAri(ari_start)
        history.add(ari_start)

        leftDrawer.closeDrawer()
    }

    private fun gotoProgressMark(preset_id: Int) {
        val progressMark = S.getDb().getProgressMarkByPresetId(preset_id) ?: return

        val ari = progressMark.ari

        if (ari != 0) {
            Tracker.trackEvent("left_drawer_progress_mark_pin_click_succeed")
            jumpToAri(ari)
            history.add(ari)
        } else {
            Tracker.trackEvent("left_drawer_progress_mark_pin_click_failed")
            MaterialDialog.Builder(this)
                .content(R.string.pm_activate_tutorial)
                .positiveText(R.string.ok)
                .show()
        }
    }

    companion object {
        const val ACTION_ATTRIBUTE_MAP_CHANGED = "yuku.alkitab.action.ATTRIBUTE_MAP_CHANGED"
        const val ACTION_ACTIVE_VERSION_CHANGED = "yuku.alkitab.base.IsiActivity.action.ACTIVE_VERSION_CHANGED"
        const val ACTION_NIGHT_MODE_CHANGED = "yuku.alkitab.base.IsiActivity.action.NIGHT_MODE_CHANGED"
        const val ACTION_NEEDS_RESTART = "yuku.alkitab.base.IsiActivity.action.NEEDS_RESTART"

        @JvmStatic
        fun createIntent(): Intent {
            return Intent(App.context, IsiActivity::class.java)
        }

        @JvmStatic
        fun createIntent(ari: Int) = Intent(App.context, IsiActivity::class.java).apply {
            action = "yuku.alkitab.action.VIEW"
            putExtra("ari", ari)
        }
    }
}
