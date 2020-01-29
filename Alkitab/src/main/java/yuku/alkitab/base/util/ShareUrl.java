package yuku.alkitab.base.util;

import android.app.Activity;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import yuku.alkitab.base.App;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

public class ShareUrl {
	public interface Callback {
		void onSuccess(String shareUrl);
		void onUserCancel();
		void onError(Exception e);
		void onFinally();
	}

	public static void make(@NonNull final Activity activity, final boolean immediatelyCancel, @NonNull final String verseText, final int ari_bc, @NonNull final IntArrayList selectedVerses_1, @NonNull final String reference, @NonNull final Version version, @Nullable final String preset_name, @NonNull final Callback callback) {
		if (immediatelyCancel) { // user explicitly ask for not submitting url
			callback.onUserCancel();
			callback.onFinally();
			return;
		}

		final StringBuilder aris = new StringBuilder();

		for (int i = 0, len = selectedVerses_1.size(); i < len; i++) {
			final int verse_1 = selectedVerses_1.get(i);
			final int ari = Ari.encodeWithBc(ari_bc, verse_1);
			if (aris.length() != 0) {
				aris.append(',');
			}
			aris.append(ari);
		}
		final FormBody.Builder form = new FormBody.Builder()
			.add("verseText", verseText)
			.add("aris", aris.toString())
			.add("verseReferences", reference);

		if (preset_name != null) {
			form.add("preset_name", preset_name);
		}

		final String versionLongName = version.getLongName();
		if (versionLongName != null) {
			form.add("versionLongName", versionLongName);
		}

		final String versionShortName = version.getShortName();
		if (versionShortName != null) {
			form.add("versionShortName", versionShortName);
		}

		final Call call = Connections.getOkHttp().newCall(
			new Request.Builder()
				.url(BuildConfig.SERVER_HOST + "v/create")
				.post(form.build())
				.build()
		);

		// when set to true, do not call any callback
		final AtomicBoolean done = new AtomicBoolean();

		final MaterialDialog dialog = new MaterialDialog.Builder(activity)
			.content("Getting share URL…")
			.progress(true, 0)
			.negativeText(R.string.cancel)
			.onNegative((dialog1, which) -> {
				if (!done.getAndSet(true)) {
					done.set(true);
					callback.onUserCancel();
					dialog1.dismiss();
					callback.onFinally();
				}
			})
			.dismissListener(dialog1 -> {
				if (!done.getAndSet(true)) {
					callback.onUserCancel();
					dialog1.dismiss();
					callback.onFinally();
				}
			})
			.show();

		call.enqueue(new okhttp3.Callback() {
			@Override
			public void onFailure(final Call call, final IOException e) {
				if (!done.getAndSet(true)) {
					activity.runOnUiThread(() -> {
						callback.onError(e);
						dialog.dismiss();
						callback.onFinally();
					});
				}
			}

			@Override
			public void onResponse(final Call call, final Response response) throws IOException {
				if (!done.getAndSet(true)) {
					final ShareUrlResponseJson obj = App.getDefaultGson().fromJson(response.body().charStream(), ShareUrlResponseJson.class);
					if (obj.success) {
						activity.runOnUiThread(() -> {
							callback.onSuccess(obj.share_url);
							dialog.dismiss();
							callback.onFinally();
						});
					} else {
						activity.runOnUiThread(() -> {
							callback.onError(new Exception(obj.message));
							dialog.dismiss();
							callback.onFinally();
						});
					}
				}
			}
		});
	}

	@Keep
	static class ShareUrlResponseJson {
		public boolean success;
		public String message;
		public String share_url;
	}
}
