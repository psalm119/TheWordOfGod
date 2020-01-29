package yuku.alkitab.base.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.SparseArray;
import android.util.Xml;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.xml.sax.Attributes;
import org.xml.sax.ext.DefaultHandler2;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.storage.InternalDb;
import static yuku.alkitab.base.util.Literals.ToStringArray;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Marker_Label;
import yuku.alkitab.util.IntArrayList;

// Imported from v3. Used for once-only migration from v3 to v4.
public class BookmarkImporter {
	static final String TAG = BookmarkImporter.class.getSimpleName();

	// constants
	static class Bookmark2_Label { // DO NOT CHANGE CONSTANT VALUES!
		public static final String XMLTAG_Bookmark2_Label = "Bukmak2_Label";
		public static final String XMLATTR_bookmark2_relId = "bukmak2_relId";
		public static final String XMLATTR_label_relId = "label_relId";
	}

	// constants
	static class BackupManager {
		public static final String XMLTAG_Bukmak2 = "Bukmak2";
		private static final String XMLATTR_ari = "ari";
		private static final String XMLATTR_kind = "jenis";
		private static final String XMLATTR_caption = "tulisan";
		private static final String XMLATTR_addTime = "waktuTambah";
		private static final String XMLATTR_modifyTime = "waktuUbah";
		private static final String XMLATTR_relId = "relId";
		private static final String XMLVAL_bookmark = "bukmak";
		private static final String XMLVAL_note = "catatan";
		private static final String XMLVAL_highlight = "stabilo";
		public static final String XMLTAG_Label = "Label";
		private static final String XMLATTR_title = "judul";
		private static final String XMLATTR_bgColor = "warnaLatar";

		@Nullable
		public static Marker markerFromAttributes(Attributes attributes) {
			int ari = Integer.parseInt(attributes.getValue("", XMLATTR_ari));
			String kind_s = attributes.getValue("", XMLATTR_kind);
			Marker.Kind kind = kind_s.equals(XMLVAL_bookmark) ? Marker.Kind.bookmark : kind_s.equals(XMLVAL_note) ? Marker.Kind.note : kind_s.equals(XMLVAL_highlight) ? Marker.Kind.highlight : null;
			String caption = unescapeHighUnicode(attributes.getValue("", XMLATTR_caption));
			Date addTime = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_addTime)));
			Date modifyTime = Sqlitil.toDate(Integer.parseInt(attributes.getValue("", XMLATTR_modifyTime)));

			if (kind == null) { // invalid
				return null;
			}

			return Marker.createNewMarker(ari, kind, caption, 1, addTime, modifyTime);
		}

		public static int getRelId(Attributes attributes) {
			String s = attributes.getValue("", XMLATTR_relId);
			return s == null ? 0 : Integer.parseInt(s);
		}

		public static Label labelFromAttributes(Attributes attributes) {
			String title = unescapeHighUnicode(attributes.getValue("", XMLATTR_title));
			String bgColor = attributes.getValue("", XMLATTR_bgColor);

			return Label.createNewLabel(title, 0, bgColor);
		}

		static ThreadLocal<Matcher> highUnicodeMatcher = new ThreadLocal<Matcher>() {
			@Override
			protected Matcher initialValue() {
				return Pattern.compile("\\[\\[~U([0-9A-Fa-f]{6})~]]").matcher("");
			}
		};

		public static String unescapeHighUnicode(String input) {
			if (input == null) return null;

			final Matcher m = highUnicodeMatcher.get();

			m.reset(input);

			StringBuffer res = new StringBuffer();
			while (m.find()) {
				String s = m.group(1);
				final int cp = Integer.parseInt(s, 16);
				m.appendReplacement(res, new String(new int[]{cp}, 0, 1));
			}
			m.appendTail(res);

			return res.toString();
		}
	}

	public static void importBookmarks(final Activity activity, @NonNull final InputStream fis, final boolean finishActivityAfterwards, final Runnable runWhenDone) {
		final MaterialDialog pd = new MaterialDialog.Builder(activity)
			.content(R.string.mengimpor_titiktiga)
			.cancelable(false)
			.progress(true, 0)
			.show();

		new AsyncTask<Boolean, Integer, Object>() {
			int count_bookmark = 0;
			int count_label = 0;

			@Override
			protected Object doInBackground(Boolean... params) {
				final List<Marker> markers = new ArrayList<>();
				final Map<Marker, Integer> markerToRelIdMap = new HashMap<>();
				final List<Label> labels = new ArrayList<>();
				final Map<Label, Integer> labelToRelIdMap = new HashMap<>();
				final SparseArray<Long> labelRelIdToAbsIdMap = new SparseArray<>();
				final SparseArray<IntArrayList> markerRelIdToLabelRelIdsMap = new SparseArray<>();

				try {

					Xml.parse(fis, Xml.Encoding.UTF_8, new DefaultHandler2() {
						@Override
						public void startElement(String uri, String localName, String qName, Attributes attributes) {
							switch (localName) {
								case BackupManager.XMLTAG_Bukmak2:
									final Marker marker = BackupManager.markerFromAttributes(attributes);
									if (marker != null) {
										markers.add(marker);
										final int bookmark2_relId = BackupManager.getRelId(attributes);
										markerToRelIdMap.put(marker, bookmark2_relId);
										count_bookmark++;
									}
									break;
								case BackupManager.XMLTAG_Label: {
									final Label label = BackupManager.labelFromAttributes(attributes);
									int label_relId = BackupManager.getRelId(attributes);
									labels.add(label);
									labelToRelIdMap.put(label, label_relId);
									count_label++;
									break;
								}
								case Bookmark2_Label.XMLTAG_Bookmark2_Label: {
									final int bookmark2_relId = Integer.parseInt(attributes.getValue("", Bookmark2_Label.XMLATTR_bookmark2_relId));
									final int label_relId = Integer.parseInt(attributes.getValue("", Bookmark2_Label.XMLATTR_label_relId));

									IntArrayList labelRelIds = markerRelIdToLabelRelIdsMap.get(bookmark2_relId);
									if (labelRelIds == null) {
										labelRelIds = new IntArrayList();
										markerRelIdToLabelRelIdsMap.put(bookmark2_relId, labelRelIds);
									}
									labelRelIds.add(label_relId);
									break;
								}
							}
						}
					});
					fis.close();
				} catch (Exception e) {
					return e;
				}

				{ // bikin label-label yang diperlukan, juga map relId dengan id dari label.
					final HashMap<String, Label> judulMap = new HashMap<>();
					final List<Label> xlabelLama = S.getDb().listAllLabels();

					for (Label labelLama : xlabelLama) {
						judulMap.put(labelLama.title, labelLama);
					}

					for (Label label : labels) {
						// cari apakah label yang judulnya persis sama udah ada
						Label labelLama = judulMap.get(label.title);
						@SuppressWarnings("ConstantConditions")
						final int labelRelId = labelToRelIdMap.get(label);
						if (labelLama != null) {
							// removed from v3: update warna label lama
							labelRelIdToAbsIdMap.put(labelRelId, labelLama._id);
							AppLog.d(TAG, "label (lama) r->a : " + labelRelId + "->" + labelLama._id);
						} else { // belum ada, harus bikin baru
							Label labelBaru = S.getDb().insertLabel(label.title, label.backgroundColor);
							labelRelIdToAbsIdMap.put(labelRelId, labelBaru._id);
							AppLog.d(TAG, "label (baru) r->a : " + labelRelId + "->" + labelBaru._id);
						}
					}
				}

				importBookmarks(markers, markerToRelIdMap, labelRelIdToAbsIdMap, markerRelIdToLabelRelIdsMap);

				return null;
			}

			@Override
			protected void onPostExecute(@NonNull Object result) {
				pd.dismiss();

				if (result instanceof Exception) {
					AppLog.e(TAG, "Error when importing markers", (Throwable) result);
					new MaterialDialog.Builder(activity)
						.content(activity.getString(R.string.terjadi_kesalahan_ketika_mengimpor_pesan, ((Exception) result).getMessage()))
						.positiveText(R.string.ok)
						.show();
				} else {
					final Dialog dialog = new MaterialDialog.Builder(activity)
						.content(activity.getString(R.string.impor_berhasil_angka_diproses, count_bookmark, count_label))
						.positiveText(R.string.ok)
						.show();

					if (finishActivityAfterwards) {
						dialog.setOnDismissListener(dialog1 -> activity.finish());
					}
				}

				if (runWhenDone != null) runWhenDone.run();
			}
		}.execute();
	}


	public static void importBookmarks(List<Marker> markers, Map<Marker, Integer> markerToRelIdMap, SparseArray<Long> labelRelIdToAbsIdMap, SparseArray<IntArrayList> markerRelIdToLabelRelIdsMap) {
		SQLiteDatabase db = S.getDb().getWritableDatabase();
		db.beginTransaction();
		try {
			final SparseArray<Marker> markerRelIdToMarker = new SparseArray<>();

			{ // write new markers (if not available yet)
				for (int i = 0; i < markers.size(); i++) {
					Marker marker = markers.get(i);
					@SuppressWarnings("ConstantConditions")
					final int marker_relId = markerToRelIdMap.get(marker);

					// migrate: look for existing marker with same kind, ari, and content
					try (Cursor cursor = db.query(
						Db.TABLE_Marker,
						null,
						Db.Marker.ari + "=? and " + Db.Marker.kind + "=? and " + Db.Marker.caption + "=?",
						ToStringArray(marker.ari, marker.kind.code, marker.caption),
						null, null, null
					)) {
						if (cursor.moveToNext()) {
							marker = InternalDb.markerFromCursor(cursor);
							markers.set(i, marker);
						} else {
							InternalDb.insertMarker(db, marker);
						}

						// map it
						markerRelIdToMarker.put(marker_relId, marker);
					}
				}
			}

			{ // now is marker-label assignments
				for (int i = 0, len = markerRelIdToLabelRelIdsMap.size(); i < len; i++) {
					final int marker_relId = markerRelIdToLabelRelIdsMap.keyAt(i);
					final IntArrayList label_relIds = markerRelIdToLabelRelIdsMap.valueAt(i);

					final Marker marker = markerRelIdToMarker.get(marker_relId);

					if (marker != null) {
						// existing labels > 0: ignore
						// existing labels == 0: insert
						final int existing_label_count = (int) DatabaseUtils.queryNumEntries(db, Db.TABLE_Marker_Label, Db.Marker_Label.marker_gid + "=?", ToStringArray(marker.gid));

						if (existing_label_count == 0) {
							for (int label_relId : label_relIds.toArray()) {
								final long label_id = labelRelIdToAbsIdMap.get(label_relId);
								if (label_id > 0) {
									final Label label = S.getDb().getLabelById(label_id);
									final Marker_Label marker_label = Marker_Label.createNewMarker_Label(marker.gid, label.gid);
									InternalDb.insertMarker_LabelIfNotExists(db, marker_label);
								} else {
									AppLog.w(TAG, "label_id is invalid!: " + label_id);
								}
							}
						}
					} else {
						AppLog.w(TAG, "wrong marker_relId: " + marker_relId);
					}
				}
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
	}
}
