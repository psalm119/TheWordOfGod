package yuku.alkitab.base.ac;

import android.os.Bundle;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;

/**
 * Transparent activity that shows verses dialog only.
 */
public class VersesDialogActivity extends BaseActivity {
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// now only supports target (decoded using TargetDecoder)
		final String target = getIntent().getStringExtra("target");
		if (target == null) {
			finish();
			return;
		}

		final IntArrayList ariRanges = TargetDecoder.decode(target);
		if (ariRanges == null) {
			new MaterialDialog.Builder(this)
				.content("Could not understand target: " + target)
				.positiveText("OK")
				.show()
				.setOnDismissListener(dialog -> finish());
			return;
		}

		final VersesDialog versesDialog = VersesDialog.newInstance(ariRanges);
		versesDialog.setListener(new VersesDialog.VersesDialogListener() {
			@Override
			public void onVerseSelected(final int ari) {
				startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari));
				finish();
			}
		});
		versesDialog.setOnDismissListener(dialog -> finish());

		versesDialog.show(getSupportFragmentManager(), "VersesDialog");
	}
}
