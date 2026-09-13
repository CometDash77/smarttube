package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Always-visible entry point for AI subtitle status and settings. */
public class AiSubtitleAction extends TwoStateAction {
    public AiSubtitleAction(Context context) {
        super(context, R.id.action_ai_subtitle, R.drawable.lb_ic_cc);

        String[] labels = new String[2];
        labels[INDEX_OFF] = context.getString(R.string.ai_subtitle_player_entry);
        labels[INDEX_ON] = context.getString(R.string.ai_subtitle_player_entry);
        setLabels(labels);
    }
}
