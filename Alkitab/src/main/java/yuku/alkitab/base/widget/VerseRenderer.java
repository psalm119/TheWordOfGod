package yuku.alkitab.base.widget;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.util.Highlights;

public class VerseRenderer {
	static final char[] superscriptDigits = {'\u2070', '\u00b9', '\u00b2', '\u00b3', '\u2074', '\u2075', '\u2076', '\u2077', '\u2078', '\u2079'};
	public static final char XREF_MARK = '\u203b';

	public static class VerseNumberSpan extends MetricAffectingSpan {
		private final boolean applyColor;
	
		public VerseNumberSpan(boolean applyColor) {
			this.applyColor = applyColor;
		}
		
		@Override public void updateMeasureState(TextPaint tp) {
			tp.baselineShift += (int) (tp.ascent() * 0.3f + 0.5f);
			tp.setTextSize(tp.getTextSize() * 0.7f);
		}
	
		@Override public void updateDrawState(TextPaint tp) {
			tp.baselineShift += (int) (tp.ascent() * 0.3f + 0.5f);
			tp.setTextSize(tp.getTextSize() * 0.7f);
			if (applyColor) {
				tp.setColor(S.applied().verseNumberColor);
			}
		}
	}

	public static class FormattedTextResult {
		public CharSequence result;
	}

	static LeadingMarginSpan.Standard createLeadingMarginSpan(int all) {
		return createLeadingMarginSpan(all, all);
	}
	
	static LeadingMarginSpan.Standard createLeadingMarginSpan(int first, int rest) {
		return new LeadingMarginSpan.Standard(first, rest);
	}
	
	private static ThreadLocal<char[]> buf_char_ = new ThreadLocal<char[]>() {
		@Override protected char[] initialValue() {
			return new char[1024];
		}
	};
	
	private static ThreadLocal<StringBuilder> buf_tag_ = new ThreadLocal<StringBuilder>() {
		@Override protected StringBuilder initialValue() {
			return new StringBuilder(100);
		}
	};

	/**
	 * @param lText TextView for verse text, but can be null if rendering is for non-display
	 * @param lVerseNumber TextView for verse number, but can be null if rendering is for non-display
	 * @param ftr optional container for result that contains the verse text with span formattings, without the verse numbers
	 * @return how many characters was used before the real start of verse text. This will be > 0 if the verse number is embedded inside lText.
	 */
	public static int render(@Nullable final TextView lText, @Nullable final TextView lVerseNumber, final int ari, @NonNull final String text, final String verseNumberText, @Nullable final Highlights.Info highlightInfo, final boolean checked, @Nullable final VerseInlineLinkSpan.Factory inlineLinkSpanFactory, @Nullable final FormattedTextResult ftr) {
		// @@ = start a verse containing paragraphs or formatting
		// @0 = start with indent 0 [paragraph]
		// @1 = start with indent 1 [paragraph]
		// @2 = start with indent 2 [paragraph]
		// @3 = start with indent 3 [paragraph]
		// @4 = start with indent 4 [paragraph]
		// @6 = start of red text [formatting]
		// @5 = end of red text   [formatting]
		// @9 = start of italic [formatting]
		// @7 = end of italic   [formatting]
		// @8 = put a blank line to the next verse [formatting]
		// @^ = start-of-paragraph marker
		// @< to @> = special tags (not visible for unsupported tags) [can be considered formatting]
		// @/ = end of special tags (closing tag) (As of 2013-10-04, all special tags must be closed) [can be considered formatting]
		
		final int text_len = text.length();
		
		// Determine if this verse text is a simple verse or formatted verse. 
		// Formatted verses start with "@@".
		// Second character must be '@' too, if not it's wrong, we will fallback to simple render.
		if (text_len < 2 || text.charAt(0) != '@' || text.charAt(1) != '@') {
			if (ftr != null) {
				ftr.result = text;
			}
			return simpleRender(lText, lVerseNumber, text, verseNumberText, highlightInfo, checked);
		}

		// optimization, to prevent repeated calls to charAt()
		char[] text_c = buf_char_.get();
		if (text_c.length < text_len) {
			text_c = new char[text_len];
			buf_char_.set(text_c);
		}
		text.getChars(0, text_len, text_c, 0);
		
		/**
		 * '0'..'4', '^' indent 0..4 or new para
		 * -1 undefined
		 */
		int paraType = -1; 
		/**
		 * position of start of paragraph
		 */
		int startPara = 0;
		/**
		 * position of start red marker
		 */
		int startRed = -1;
		/**
		 * position of start italic marker
		 */
		int startItalic = -1;
		/**
		 * whether we are inside a tag (between @< and @>)
		 */
		boolean inSpecialTag = false;
		/**
		 * Reusable tag buffer
		 */
		final StringBuilder tag = buf_tag_.get();

		final SpannableStringBuilder sb = new SpannableStringBuilder();

		// this has two uses
		// - to check whether a verse number has been written
		// - to check whether we need to put a new line when encountering a new para
		final int startPosAfterVerseNumber;

		int pos = 2; // we start after "@@"

		// write verse number inline only when no @[1234^] on the beginning of text
		if (text_len >= 4 && text_c[pos] == '@' && (text_c[pos+1] == '^' || (text_c[pos+1] >= '1' && text_c[pos+1] <= '4'))) {
			// don't write verse number now
			startPosAfterVerseNumber = 0;
		} else {
			sb.append(verseNumberText);
			sb.setSpan(new VerseRenderer.VerseNumberSpan(!checked), 0, sb.length(), 0);
			sb.append("  ");
			startPosAfterVerseNumber = sb.length();
		}

		// initialize lVerseNumber to have no padding first
		if (lVerseNumber != null) {
			lVerseNumber.setPadding(0, 0, 0, 0);
		}

		while (true) {
			if (pos >= text_len) {
				break;
			}

			int nextAt = text.indexOf('@', pos);

			if (nextAt == -1) { // no more, just append till the end of everything and exit
				sb.append(text, pos, text_len);
				break;
			}

			if (inSpecialTag) { // are we in a tag?
				// we have encountered the end of a tag
				tag.setLength(0);
				tag.append(text, pos, nextAt);
				pos = nextAt;
			} else {
				// insert all text until the nextAt
				if (nextAt != pos) /* extra check for optimization (prevent call to sb.append()) */ {
					sb.append(text, pos, nextAt);
					pos = nextAt;
				}
			}
			
			pos++;
			// just in case 
			if (pos >= text_len) {
				break;
			}

			char marker = text_c[pos];
			switch (marker) {
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '^':
					// apply previous
					applyParaStyle(sb, paraType, startPara, verseNumberText, startPosAfterVerseNumber > 0);
					if (sb.length() > startPosAfterVerseNumber) {
						sb.append("\n");
					}
					// store current
					paraType = marker;
					startPara = sb.length();
					break;
				case '6':
					startRed = sb.length();
					break;
				case '5':
					if (startRed != -1) {
						if (!checked) {
							sb.setSpan(new ForegroundColorSpan(S.applied().fontRedColor), startRed, sb.length(), 0);
						}
						startRed = -1;
					}
					break;
				case '9':
					startItalic = sb.length();
					break;
				case '7':
					if (startItalic != -1) {
						sb.setSpan(new StyleSpan(Typeface.ITALIC), startItalic, sb.length(), 0);
						startItalic = -1;
					}
					break;
				case '8':
					sb.append("\n");
					break;
				case '<':
					inSpecialTag = true;
					break;
				case '>':
					inSpecialTag = false;
					break;
				case '/':
					processSpecialTag(sb, tag, inlineLinkSpanFactory, ari);
					break;
			}

			pos++;
		}
		
		// apply unapplied
		applyParaStyle(sb, paraType, startPara, verseNumberText, startPosAfterVerseNumber > 0);

		if (highlightInfo != null) {
			final BackgroundColorSpan span = new BackgroundColorSpan(Highlights.alphaMix(highlightInfo.colorRgb));
			if (highlightInfo.shouldRenderAsPartialForVerseText(sb.subSequence(startPosAfterVerseNumber, sb.length()))) {
				final int start = startPosAfterVerseNumber + highlightInfo.partial.startOffset;
				final int end = startPosAfterVerseNumber + highlightInfo.partial.endOffset;
				if (end > start) {
					sb.setSpan(span, start, end, 0);
				} else {
					sb.setSpan(span, end, start, 0);
				}
			} else {
				sb.setSpan(span, startPosAfterVerseNumber, sb.length(), 0);
			}
		}

		if (lText != null) {
			lText.setText(sb);
		}
		
		// show verse on lVerseNumber if not shown in lText yet
		if (lVerseNumber != null) {
			if (startPosAfterVerseNumber > 0) {
				lVerseNumber.setVisibility(View.GONE);
				lVerseNumber.setText("");
			} else {
				lVerseNumber.setVisibility(View.VISIBLE);
				lVerseNumber.setText(verseNumberText);
			}
		}

		if (ftr != null) {
			if (startPosAfterVerseNumber == 0) {
				ftr.result = sb;
			} else {
				ftr.result = sb.subSequence(startPosAfterVerseNumber, sb.length());
			}
		}

		return startPosAfterVerseNumber;
	}

	static void processSpecialTag(final SpannableStringBuilder sb, final StringBuilder tag, @Nullable final VerseInlineLinkSpan.Factory inlineLinkSpanFactory, final int ari) {
		final int sb_len = sb.length();
		if (tag.length() >= 2) {
			// Footnote
			if (tag.charAt(0) == 'f') {
				try {
					final int field = Integer.parseInt(tag.substring(1));
					if (field < 1 || field > 255) {
						throw new NumberFormatException();
					}
					appendSuperscriptNumber(sb, field);
					if (inlineLinkSpanFactory != null) {
						sb.setSpan(inlineLinkSpanFactory.create(VerseInlineLinkSpan.Type.footnote, ari << 8 | field), sb_len, sb.length(), 0);
					}
				} catch (NumberFormatException e) {
					reportInvalidSpecialTag("Invalid footnote tag at ari 0x" + Integer.toHexString(ari) + ": " + tag);
				}
			} else if (tag.charAt(0) == 'x') {
				try {
					final int field = Integer.parseInt(tag.substring(1));
					if (field < 1 || field > 255) {
						throw new NumberFormatException();
					}
					sb.append(XREF_MARK); // star mark
					if (inlineLinkSpanFactory != null) {
						sb.setSpan(inlineLinkSpanFactory.create(VerseInlineLinkSpan.Type.xref, ari << 8 | field), sb_len, sb.length(), 0);
					}
				} catch (NumberFormatException e) {
					reportInvalidSpecialTag("Invalid xref tag at ari 0x" + Integer.toHexString(ari) + ": " + tag);
				}
			}
		}
	}

	static Toast invalidSpecialTagToast;

	static void reportInvalidSpecialTag(final String msg) {
		new Handler(Looper.getMainLooper()).post(() -> {
			if (invalidSpecialTagToast == null) {
				invalidSpecialTagToast = Toast.makeText(App.context, msg, Toast.LENGTH_SHORT);
			} else {
				invalidSpecialTagToast.setText(msg);
			}
			invalidSpecialTagToast.show();
		});
	}

	public static void appendSuperscriptNumber(final SpannableStringBuilder sb, final int field) {
		if (field >= 0 && field < 10) {
			sb.append(superscriptDigits[field]);
		} else if (field >= 10) {
			final String s = String.valueOf(field);
			for (int i = 0; i < s.length(); i++) {
				final char c = s.charAt(i);
				sb.append(superscriptDigits[c - '0']);
			}
		}
		// should not be negative
	}

	/**
	 * @param paraType if -1, will apply the same thing as when paraType is 0 and firstLineWithVerseNumber is true.
	 * @param firstLineWithVerseNumber If this is formatting for the first paragraph of a verse and that paragraph contains a verse number, so we can apply more lefty first-line indent.
	 * This only applies if the paraType is 0.
	 */
	static void applyParaStyle(SpannableStringBuilder sb, int paraType, int startPara, String verseNumberText, boolean firstLineWithVerseNumber) {
		int len = sb.length();
		
		if (startPara == len) return;

		final int indentSpacingExtraUnits = verseNumberText.length() < 3 ? 0 : verseNumberText.length() - 2;
		final S.CalculatedDimensions applied = S.applied();

		switch (paraType) {
		case -1:
			sb.setSpan(createLeadingMarginSpan(0, applied.indentParagraphRest), startPara, len, 0);
			break;
		case '0':
			if (firstLineWithVerseNumber) {
				sb.setSpan(createLeadingMarginSpan(0, applied.indentParagraphRest), startPara, len, 0);
			} else {
				sb.setSpan(createLeadingMarginSpan(applied.indentParagraphRest), startPara, len, 0);
			}
			break;
		case '1':
			sb.setSpan(createLeadingMarginSpan(applied.indentSpacing1 + indentSpacingExtraUnits * applied.indentSpacingExtra), startPara, len, 0);
			break;
		case '2':
			sb.setSpan(createLeadingMarginSpan(applied.indentSpacing2 + indentSpacingExtraUnits * applied.indentSpacingExtra), startPara, len, 0);
			break;
		case '3':
			sb.setSpan(createLeadingMarginSpan(applied.indentSpacing3 + indentSpacingExtraUnits * applied.indentSpacingExtra), startPara, len, 0);
			break;
		case '4':
			sb.setSpan(createLeadingMarginSpan(applied.indentSpacing4 + indentSpacingExtraUnits * applied.indentSpacingExtra), startPara, len, 0);
			break;
		case '^':
			sb.setSpan(createLeadingMarginSpan(applied.indentParagraphFirst, applied.indentParagraphRest), startPara, len, 0);
			break;
		}
	}

	/**
	 * @return how many characters was used before the real start of verse text. This will be > 0 if the verse number is embedded inside lText.
	 */
	public static int simpleRender(@Nullable TextView lText, @Nullable TextView lVerseNumber, String text, String verseNumberText, @Nullable final Highlights.Info highlightInfo, boolean checked) {
		final SpannableStringBuilder sb = new SpannableStringBuilder();

		// verse number
		sb.append(verseNumberText).append("  ");
		sb.setSpan(new VerseRenderer.VerseNumberSpan(!checked), 0, verseNumberText.length(), 0);
		final int startPosAfterVerseNumber = sb.length();

		// verse text
		sb.append(text);
		sb.setSpan(createLeadingMarginSpan(0, S.applied().indentParagraphRest), 0, sb.length(), 0);

		if (highlightInfo != null) {
			final BackgroundColorSpan span = new BackgroundColorSpan(Highlights.alphaMix(highlightInfo.colorRgb));
			if (highlightInfo.shouldRenderAsPartialForVerseText(text)) {
				final int start = startPosAfterVerseNumber + highlightInfo.partial.startOffset;
				final int end = startPosAfterVerseNumber + highlightInfo.partial.endOffset;
				if (end > start) {
					sb.setSpan(span, start, end, 0);
				} else {
					sb.setSpan(span, end, start, 0);
				}
			} else {
				sb.setSpan(span, startPosAfterVerseNumber, sb.length(), 0);
			}
		}

		if (lText != null) {
			lText.setText(sb);
		}

		// initialize lVerseNumber to have no padding first
		if (lVerseNumber != null) {
			lVerseNumber.setPadding(0, 0, 0, 0);
			lVerseNumber.setVisibility(View.GONE);
			lVerseNumber.setText("");
		}

		return startPosAfterVerseNumber;
	}
}
