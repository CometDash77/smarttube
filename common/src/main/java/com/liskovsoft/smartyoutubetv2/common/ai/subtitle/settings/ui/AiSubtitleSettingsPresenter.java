package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.AiSubtitleCueBridge;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.integration.AiSubtitleRuntime;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ConnectionTestResult;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ModelCatalog;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderPreset;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProfileResolver;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderProtocol;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.ProviderType;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.provider.http.OkHttpRequestExecutor;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.prompt.PromptProfile;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleDisplayMode;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.remote.AiSubtitlePhoneInputServer;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.remote.AiSubtitleQrCode;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feature-owned subtitle settings entry point used by the single authorized upstream host
 * hook. It owns the enable switch and the Provider Profile management dialogs.
 */
public class AiSubtitleSettingsPresenter extends BasePresenter<Void> {
    private AiSubtitleSettingsPresenter(Context context) {
        super(context);
    }

    public static AiSubtitleSettingsPresenter instance(Context context) {
        return new AiSubtitleSettingsPresenter(context);
    }

    /**
     * Appends the enable switch and Provider Profile entry into the existing subtitle
     * settings dialog. Disabling still cancels in-flight work immediately.
     */
    public void append(AppDialogPresenter settingsPresenter) {
        AiSubtitleData data = AiSubtitleData.instance(getContext());

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_enable),
                option -> {
                    data.setEnabled(option.isSelected());
                    AiSubtitleCueBridge.instance(getContext())
                            .onEnabledChanged(option.isSelected());
                },
                data.isEnabled()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_provider_profiles),
                option -> showProviderProfiles()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_phone_input),
                option -> showPhoneInput()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_prompt_profiles),
                option -> showPromptProfiles()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_target_language),
                option -> showTargetLanguage()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_display_mode),
                option -> showDisplayMode()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_bilingual_order),
                option -> showBilingualOrder()));

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_context),
                option -> {
                    data.setContextEnabled(option.isSelected());
                    AiSubtitleCueBridge.instance(getContext())
                            .onContextEnabledChanged(option.isSelected());
                },
                data.isContextEnabled()));

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_streaming),
                option -> {
                    data.setStreamingEnabled(option.isSelected());
                    AiSubtitleCueBridge.instance(getContext())
                            .onStreamingEnabledChanged(option.isSelected());
                },
                data.isStreamingEnabled()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_lookahead),
                option -> showLookahead()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_throttle),
                option -> showScheduleThrottle()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_segmentation),
                option -> showSegmentation()));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_retry),
                option -> AiSubtitleCueBridge.instance(getContext()).retryFailed()));
    }

    private void showProviderProfiles() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();

        ProviderProfilesPresenter profiles = profilesPresenter();
        List<ProviderProfile> list = profiles.getProfiles();

        if (list.isEmpty()) {
            presenter.appendSingleButton(UiOptionItem.from(
                    getContext().getString(R.string.ai_subtitle_add_profile),
                    option -> showCreateProfileDialog()));
        } else {
            List<OptionItem> selection = new ArrayList<>();
            for (ProviderProfile profile : list) {
                boolean selected = profile.getId().equals(profiles.getSelectedProfileId());
                selection.add(UiOptionItem.from(profile.getName(), option -> {
                    if (profiles.select(profile.getId())) {
                        AiSubtitleRuntime.applyToBridge(getContext());
                        showProviderProfiles();
                    }
                }, selected));
            }
            presenter.appendRadioCategory(
                    getContext().getString(R.string.ai_subtitle_provider_profiles), selection);
            presenter.appendSingleButton(UiOptionItem.from(
                    getContext().getString(R.string.ai_subtitle_add_profile),
                    option -> showCreateProfileDialog()));

            for (ProviderProfile profile : list) {
                presenter.appendStringsCategory(profile.getName(), actions(profiles, profile));
            }
        }

        presenter.showDialog(getContext().getString(R.string.ai_subtitle_provider_profiles));
    }

    private List<OptionItem> actions(final ProviderProfilesPresenter profiles,
                                     final ProviderProfile profile) {
        List<OptionItem> actions = new ArrayList<>();

        actions.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_edit_profile),
                option -> showEditProfileDialog(profile)));

        actions.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_copy_profile),
                option -> {
                    if (profiles.copy(profile.getId()).isSuccess()) {
                        AiSubtitleRuntime.applyToBridge(getContext());
                        showProviderProfiles();
                    }
                }));

        actions.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_change_secret),
                option -> showChangeSecretDialog(profile)));

        actions.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_test_connection),
                option -> showTestConnection(profile)));

        actions.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_set_default),
                option -> {
                    if (profiles.setDefault(profile.getId())) {
                        showProviderProfiles();
                    }
                }));

        actions.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_delete_profile),
                option -> {
                    if (profiles.delete(profile.getId())) {
                        AiSubtitleRuntime.applyToBridge(getContext());
                        showProviderProfiles();
                    }
                }));

        return actions;
    }

    private void showCreateProfileDialog() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();

        List<OptionItem> types = new ArrayList<>();
        for (ProviderType type : ProviderType.values()) {
            if (type == ProviderType.CUSTOM) {
                types.add(UiOptionItem.from(ProviderPreset.forType(type).getDisplayName(),
                        option -> showCreateCustomProtocol()));
            } else {
                ProviderPreset preset = ProviderPreset.forType(type);
                types.add(UiOptionItem.from(preset.getDisplayName(),
                        option -> showCreateName(type, preset.getProtocol())));
            }
        }

        presenter.appendRadioCategory(
                getContext().getString(R.string.ai_subtitle_choose_provider_type), types);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_add_profile));
    }

    private void showCreateCustomProtocol() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();

        List<OptionItem> protocols = new ArrayList<>();
        protocols.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_protocol_openai),
                option -> showCreateName(ProviderType.CUSTOM,
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS)));
        protocols.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_protocol_anthropic),
                option -> showCreateName(ProviderType.CUSTOM,
                        ProviderProtocol.ANTHROPIC_MESSAGES)));
        presenter.appendRadioCategory(
                getContext().getString(R.string.ai_subtitle_choose_protocol), protocols);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_choose_protocol));
    }

    private void showCreateName(final ProviderType type, final ProviderProtocol protocol) {
        final ProviderProfileEditor editor = ProviderProfileEditor.create(type, protocol);
        final String defaultBaseUrl = ProviderPreset.forType(type, protocol).getBaseUrl();

        SimpleEditDialog.show(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_name),
                "", newValue -> {
                    editor.setName(newValue);
                    showCreateBaseUrl(editor, defaultBaseUrl);
                    return true;
                });
    }

    private void showCreateBaseUrl(final ProviderProfileEditor editor, final String defaultBaseUrl) {
        SimpleEditDialog.show(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_base_url),
                defaultBaseUrl != null ? defaultBaseUrl : "", newValue -> {
                    editor.setBaseUrl(newValue);
                    showCreateModel(editor);
                    return true;
                });
    }

    private void showCreateModel(final ProviderProfileEditor editor) {
        SimpleEditDialog.show(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_model),
                "", newValue -> {
                    editor.setModelId(newValue);
                    showCreateSecret(editor);
                    return true;
                });
    }

    private void showCreateSecret(final ProviderProfileEditor editor) {
        SimpleEditDialog.showPassword(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_secret),
                "", newValue -> {
                    editor.setSecret(newValue);
                    ProviderProfilesPresenter.SaveResult result = profilesPresenter().save(
                            null, editor.getName(), editor.getProviderType(),
                            editor.getProtocol(), editor.getBaseUrl(), editor.getModelId(),
                            editor.getSecret(), true);
                    if (result.isSuccess()) {
                        AiSubtitleRuntime.applyToBridge(getContext());
                    }
                    return result.isSuccess();
                });
    }

    private void showEditProfileDialog(final ProviderProfile profile) {
        final ProviderProfileEditor editor = ProviderProfileEditor.edit(profile);

        SimpleEditDialog.show(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_name),
                editor.getName(), newValue -> {
                    editor.setName(newValue);
                    showEditBaseUrl(editor, profile);
                    return true;
                });
    }

    private void showEditBaseUrl(final ProviderProfileEditor editor,
                                 final ProviderProfile profile) {
        SimpleEditDialog.show(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_base_url),
                editor.getBaseUrl(), newValue -> {
                    editor.setBaseUrl(newValue);
                    showEditModel(editor, profile);
                    return true;
                });
    }

    private void showEditModel(final ProviderProfileEditor editor,
                               final ProviderProfile profile) {
        SimpleEditDialog.show(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_model),
                editor.getModelId(), newValue -> {
                    editor.setModelId(newValue);
                    if (!editor.hasUnsavedChanges()) {
                        return true;
                    }
                    ProviderProfilesPresenter.SaveResult result = profilesPresenter().save(
                            profile.getId(), editor.getName(), editor.getProviderType(),
                            editor.getProtocol(), editor.getBaseUrl(), editor.getModelId(),
                            null, false);
                    if (result.isSuccess()) {
                        AiSubtitleRuntime.applyToBridge(getContext());
                    }
                    return result.isSuccess();
                });
    }

    private void showChangeSecretDialog(final ProviderProfile profile) {
        SimpleEditDialog.showPassword(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_secret),
                "", newValue -> {
                    ProviderProfilesPresenter.SaveResult result = profilesPresenter().save(
                            profile.getId(), profile.getName(), profile.getProviderType(),
                            profile.getProtocol(), profile.getBaseUrl(), profile.getModelId(),
                            newValue, true);
                    if (result.isSuccess()) {
                        AiSubtitleRuntime.applyToBridge(getContext());
                    }
                    return result.isSuccess();
                });
    }

    private void showTestConnection(final ProviderProfile profile) {
        final AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();
        final ProviderProfilesPresenter.ConnectionTest[] handle =
                new ProviderProfilesPresenter.ConnectionTest[1];
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicBoolean delivered = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());

        presenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_cancel),
                option -> {
                    cancelled.set(true);
                    if (handle[0] != null) {
                        handle[0].cancel();
                    }
                    presenter.closeDialog();
                }));

        handle[0] = profilesPresenter().testTranslationConnection(profile,
                AiSubtitleData.instance(getContext()).getTargetLanguage(),
                new ProviderProfilesPresenter.ConnectionTestListener() {
                    @Override
                    public void onStarted() {
                        handler.post(() -> {
                            if (!cancelled.get() && !delivered.get()) {
                                presenter.showDialog(getContext().getString(
                                        R.string.ai_subtitle_test_connection_running));
                            }
                        });
                    }

                    @Override
                    public void onResult(ConnectionTestResult result) {
                        handler.post(() -> {
                            if (cancelled.get() || delivered.getAndSet(true)) {
                                return;
                            }
                            showConnectionResult(result);
                        });
                    }
                });
    }

    private void showConnectionResult(ConnectionTestResult result) {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();

        String title = getContext().getString(result.isSuccess()
                ? R.string.ai_subtitle_test_connection_success
                : R.string.ai_subtitle_test_connection_failed);
        presenter.appendStringsCategory(title, new ArrayList<>());

        String reason = result.getFailure() != null
                ? result.getFailure().getMessage() : null;
        if (!result.isSuccess() && reason != null && !reason.trim().isEmpty()) {
            presenter.appendStringsCategory(reason, new ArrayList<>());
        }

        presenter.showDialog(title);
    }

    private ProviderProfilesPresenter profilesPresenter() {
        AiSubtitleData data = AiSubtitleData.instance(getContext());
        SecretStore secrets = data.secrets();
        ProviderProfileResolver resolver =
                new ProviderProfileResolver(new OkHttpRequestExecutor(), secrets);
        return new ProviderProfilesPresenter(data.providerProfiles(), secrets, resolver,
                new ModelCatalog(new OkHttpRequestExecutor()));
    }

    private void showTargetLanguage() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();
        final TranslationProfilePresenter languages = new TranslationProfilePresenter(
                AiSubtitleData.instance(getContext()));
        List<OptionItem> options = new ArrayList<>();
        for (final String language : languages.getTargetLanguages()) {
            options.add(UiOptionItem.from(language, option -> {
                if (languages.setTargetLanguage(language)) {
                    AiSubtitleRuntime.applyToBridge(getContext());
                    showTargetLanguage();
                }
            }, language.equals(languages.getTargetLanguage())));
        }
        presenter.appendRadioCategory(getContext().getString(R.string.ai_subtitle_target_language), options);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_target_language));
    }

    private void showPhoneInput() {
        AiSubtitleData data = AiSubtitleData.instance(getContext());
        ProviderProfilesPresenter profiles = profilesPresenter();
        ProviderProfile profile = profiles.getProfile(profiles.getSelectedProfileId());
        PromptProfilesPresenter promptProfiles = new PromptProfilesPresenter(data.prompts());
        PromptProfile prompt = promptProfiles.getProfile(promptProfiles.getSelectedProfileId());

        final TextView statusView = new TextView(getContext());
        statusView.setText(getContext().getString(R.string.ai_subtitle_phone_starting));
        statusView.setPadding(dp(24), dp(8), dp(24), dp(8));

        final ImageView qrView = new ImageView(getContext());
        qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrView.setAdjustViewBounds(true);

        final TextView draftView = new TextView(getContext());
        draftView.setPadding(dp(24), dp(16), dp(24), dp(8));
        draftView.setTextIsSelectable(false);

        final LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(qrView, new LinearLayout.LayoutParams(dp(300), dp(300)));
        layout.addView(draftView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AiSubtitlePhoneInputServer[] server = new AiSubtitlePhoneInputServer[1];
        final Handler handler = new Handler(Looper.getMainLooper());
        final AlertDialog[] dialog = new AlertDialog[1];

        server[0] = AiSubtitlePhoneInputServer.start(getContext(),
                new AiSubtitlePhoneInputServer.Listener() {
                    @Override
                    public void onConnected(String address) {
                        handler.post(() -> {
                            Bitmap qr = AiSubtitleQrCode.create(address, 600);
                            if (qr != null) {
                                qrView.setImageBitmap(qr);
                            }
                            statusView.setText(getContext().getString(
                                    R.string.ai_subtitle_phone_scan) + "\n" + address);
                            updatePhoneDraft(draftView, server[0].getDraft());
                        });
                    }

                    @Override
                    public void onDraftChanged(final AiSubtitlePhoneInputServer.Draft draft,
                                               final boolean connected) {
                        handler.post(() -> {
                            statusView.setText(phoneStatus(connected, draft));
                            updatePhoneDraft(draftView, draft);
                        });
                    }

                    @Override
                    public void onSaved(final AiSubtitlePhoneInputServer.Draft draft,
                                        final boolean success) {
                        handler.post(() -> {
                            statusView.setText(phoneStatus(server[0].isClientConnected(), draft));
                            updatePhoneDraft(draftView, draft);
                            if (success) {
                                AiSubtitleRuntime.applyToBridge(getContext());
                            }
                        });
                    }
                }, profile, prompt);

        if (server[0] == null) {
            MessageHelpers.showMessage(getContext(),
                    getContext().getString(R.string.ai_subtitle_phone_start_failed));
            return;
        }

        dialog[0] = new AlertDialog.Builder(getContext())
                .setTitle(R.string.ai_subtitle_phone_input)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(d -> server[0].close())
                .create();
        dialog[0].show();
    }

    private String phoneStatus(boolean connected,
                               AiSubtitlePhoneInputServer.Draft draft) {
        if (draft == null) {
            return getContext().getString(connected
                    ? R.string.ai_subtitle_phone_connected
                    : R.string.ai_subtitle_phone_waiting);
        }

        StringBuilder status = new StringBuilder();
        if (draft.saveAttempted && !draft.saveSucceeded) {
            status.append(draft.lastError != null ? draft.lastError
                    : getContext().getString(R.string.ai_subtitle_phone_save_failed));
        } else if (draft.saveSucceeded) {
            status.append(getContext().getString(R.string.ai_subtitle_phone_saved));
        } else {
            status.append(getContext().getString(connected
                    ? R.string.ai_subtitle_phone_connected
                    : R.string.ai_subtitle_phone_waiting));
        }

        if (draft.testSucceeded) {
            status.append('\n').append(getContext().getString(
                    R.string.ai_subtitle_test_connection_success));
        } else if (draft.testFailed) {
            status.append('\n').append(getContext().getString(
                    R.string.ai_subtitle_test_connection_failed));
            if (!TextUtils.isEmpty(draft.testError)) {
                status.append('\n').append(draft.testError);
            }
        }
        return status.toString();
    }

    private void updatePhoneDraft(TextView draftView,
                                 AiSubtitlePhoneInputServer.Draft draft) {
        if (draftView == null || draft == null) {
            return;
        }

        Context context = getContext();
        boolean secretSet = draft.originalSecretSet
                || !TextUtils.isEmpty(draft.secret);
        draftView.setText(context.getString(R.string.ai_subtitle_phone_draft)
                + "\n" + context.getString(R.string.ai_subtitle_profile_name) + ": "
                + shortDraftValue(draft.name)
                + "\n" + context.getString(R.string.ai_subtitle_profile_base_url) + ": "
                + shortDraftValue(draft.baseUrl)
                + "\n" + context.getString(R.string.ai_subtitle_profile_model) + ": "
                + shortDraftValue(draft.modelId)
                + "\n" + context.getString(R.string.ai_subtitle_profile_secret) + ": "
                + context.getString(secretSet
                ? R.string.ai_subtitle_phone_secret_set
                : R.string.ai_subtitle_phone_secret_missing)
                + "\n" + context.getString(R.string.ai_subtitle_prompt_name) + ": "
                + shortDraftValue(draft.promptName)
                + "\n" + context.getString(R.string.ai_subtitle_prompt_content) + ": "
                + shortDraftValue(draft.promptContent));
    }

    private String shortDraftValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        String clean = value.replace('\n', ' ').trim();
        return clean.length() <= 80 ? clean : clean.substring(0, 77) + "…";
    }

    private int dp(int value) {

        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private void showDisplayMode() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();

        List<OptionItem> modes = new ArrayList<>();
        for (final AiSubtitleDisplayMode mode : AiSubtitleDisplayMode.values()) {
            modes.add(UiOptionItem.from(displayModeName(mode), option -> {
                if (mode != AiSubtitleData.instance(getContext()).getDisplayMode()) {
                    AiSubtitleData.instance(getContext()).setDisplayMode(mode);
                    AiSubtitleCueBridge.instance(getContext()).setDisplayMode(mode);
                }
                showDisplayMode();
            }, mode == AiSubtitleData.instance(getContext()).getDisplayMode()));
        }

        presenter.appendRadioCategory(
                getContext().getString(R.string.ai_subtitle_display_mode), modes);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_display_mode));
    }

    private String displayModeName(AiSubtitleDisplayMode mode) {
        switch (mode) {
            case SOURCE:
                return getContext().getString(R.string.ai_subtitle_display_mode_source);
            case TRANSLATION_ONLY:
                return getContext().getString(R.string.ai_subtitle_display_mode_translation_only);
            case BILINGUAL:
            default:
                return getContext().getString(R.string.ai_subtitle_display_mode_bilingual);
        }
    }

    private void showLookahead() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();
        AiSubtitleData data = AiSubtitleData.instance(getContext());
        List<OptionItem> options = new ArrayList<>();
        for (final int seconds : new int[] {0, 30, 60, 90, 120}) {
            options.add(UiOptionItem.from(seconds + " s", option -> {
                data.setLookaheadSeconds(seconds);
                AiSubtitleCueBridge.instance(getContext()).onSchedulingChanged(
                        seconds * 1_000L, data.getScheduleThrottleSeconds() * 1_000L);
                showLookahead();
            }, seconds == data.getLookaheadSeconds()));
        }
        presenter.appendRadioCategory(
                getContext().getString(R.string.ai_subtitle_lookahead), options);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_lookahead));
    }

    private void showScheduleThrottle() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();
        AiSubtitleData data = AiSubtitleData.instance(getContext());
        List<OptionItem> options = new ArrayList<>();
        for (final int seconds : new int[] {5, 15, 30}) {
            options.add(UiOptionItem.from(seconds + " s", option -> {
                data.setScheduleThrottleSeconds(seconds);
                AiSubtitleCueBridge.instance(getContext()).onSchedulingChanged(
                        data.getLookaheadSeconds() * 1_000L, seconds * 1_000L);
                showScheduleThrottle();
            }, seconds == data.getScheduleThrottleSeconds()));
        }
        presenter.appendRadioCategory(
                getContext().getString(R.string.ai_subtitle_throttle), options);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_throttle));
    }

    private void showSegmentation() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();
        final AiSubtitleData data = AiSubtitleData.instance(getContext());
        int[][] presets = {{40, 120, 60}, {60, 200, 80}, {100, 320, 100}};
        List<OptionItem> options = new ArrayList<>();
        for (final int[] preset : presets) {
            options.add(UiOptionItem.from(
                    preset[0] + "/" + preset[1] + ", long " + preset[2], option -> {
                        data.setSegmentLimits(preset[0], preset[1], preset[2]);
                        AiSubtitleCueBridge.instance(getContext()).onSegmentationChanged(
                                preset[0], preset[1], preset[2]);
                        showSegmentation();
                    }, preset[0] == data.getSegmentTargetChars()
                            && preset[1] == data.getSegmentMaxChars()
                            && preset[2] == data.getLongSentenceChars()));
        }

        // A custom limit set from the phone stays visible instead of being rewritten to a preset.
        if (!isSegmentationPreset(data, presets)) {
            options.add(UiOptionItem.from(
                    getContext().getString(R.string.ai_subtitle_segmentation_custom)
                            + " (" + data.getSegmentTargetChars() + "/" + data.getSegmentMaxChars()
                            + ", " + data.getLongSentenceChars() + ")",
                    option -> showSegmentation(), true));
        }

        presenter.appendRadioCategory(
                getContext().getString(R.string.ai_subtitle_segmentation), options);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_segmentation));
    }

    private static boolean isSegmentationPreset(AiSubtitleData data, int[][] presets) {
        for (int[] preset : presets) {
            if (preset[0] == data.getSegmentTargetChars()
                    && preset[1] == data.getSegmentMaxChars()
                    && preset[2] == data.getLongSentenceChars()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bilingual order is presentation only: it repaints the current cue list without issuing a
     * request or dropping cached translations.
     */
    private void showBilingualOrder() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();
        final AiSubtitleData data = AiSubtitleData.instance(getContext());

        List<OptionItem> options = new ArrayList<>();
        options.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_order_source_first),
                option -> {
                    applyBilingualOrder(false);
                    showBilingualOrder();
                }, !data.isTranslationFirst()));
        options.add(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_order_translation_first),
                option -> {
                    applyBilingualOrder(true);
                    showBilingualOrder();
                }, data.isTranslationFirst()));

        presenter.appendRadioCategory(
                getContext().getString(R.string.ai_subtitle_bilingual_order), options);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_bilingual_order));
    }

    private void applyBilingualOrder(boolean translationFirst) {
        AiSubtitleData.instance(getContext()).setTranslationFirst(translationFirst);
        AiSubtitleCueBridge.instance(getContext()).setTranslationFirst(translationFirst);
    }

    private void showPromptProfiles() {
        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());
        presenter.closeDialog();
        final PromptProfilesPresenter prompts = new PromptProfilesPresenter(
                AiSubtitleData.instance(getContext()).prompts());
        List<PromptProfile> list = prompts.getProfiles();
        List<OptionItem> selection = new ArrayList<>();
        for (final PromptProfile profile : list) {
            selection.add(UiOptionItem.from(profile.getName(), option -> {
                if (prompts.select(profile.getId())) {
                    AiSubtitleRuntime.applyToBridge(getContext());
                    showPromptProfiles();
                }
            }, profile.getId().equals(prompts.getSelectedProfileId())));
        }
        presenter.appendRadioCategory(getContext().getString(R.string.ai_subtitle_prompt_profiles), selection);
        presenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_add_prompt), option -> showCreatePrompt()));
        for (final PromptProfile profile : list) {
            List<OptionItem> actions = new ArrayList<>();
            actions.add(UiOptionItem.from(getContext().getString(
                    profile.isBuiltIn() ? R.string.ai_subtitle_copy_prompt : R.string.ai_subtitle_edit_prompt),
                    option -> {
                        if (profile.isBuiltIn()) {
                            showCopyPrompt(profile);
                        } else {
                            showEditPrompt(profile);
                        }
                    }));
            if (!profile.isBuiltIn()) {
                actions.add(UiOptionItem.from(getContext().getString(R.string.ai_subtitle_delete_prompt), option -> {
                    if (prompts.delete(profile.getId())) {
                        AiSubtitleRuntime.applyToBridge(getContext());
                        showPromptProfiles();
                    }
                }));
            }
            presenter.appendStringsCategory(profile.getName(), actions);
        }
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_prompt_profiles));
    }

    private void showCreatePrompt() {
        final PromptProfileEditorPresenter editor = PromptProfileEditorPresenter.create();
        SimpleEditDialog.show(getContext(), getContext().getString(R.string.ai_subtitle_prompt_name), "",
                value -> { editor.setName(value); showPromptContent(editor, false); return true; });
    }

    private void showCopyPrompt(final PromptProfile profile) {
        SimpleEditDialog.show(getContext(), getContext().getString(R.string.ai_subtitle_prompt_name),
                profile.getName() + " copy", value -> {
                    PromptProfilesPresenter.SaveResult result = new PromptProfilesPresenter(
                            AiSubtitleData.instance(getContext()).prompts()).copy(profile.getId(), value);
                    if (result.isSuccess()) { AiSubtitleRuntime.applyToBridge(getContext()); showPromptProfiles(); }
                    return result.isSuccess();
                });
    }

    private void showEditPrompt(final PromptProfile profile) {
        final PromptProfileEditorPresenter editor = PromptProfileEditorPresenter.edit(profile);
        SimpleEditDialog.show(getContext(), getContext().getString(R.string.ai_subtitle_prompt_name),
                editor.getName(), value -> { editor.setName(value); showPromptContent(editor, true); return true; });
    }

    private void showPromptContent(final PromptProfileEditorPresenter editor, final boolean update) {
        SimpleEditDialog.show(getContext(), getContext().getString(R.string.ai_subtitle_prompt_content),
                editor.getContent(), value -> {
                    editor.setContent(value);
                    PromptProfilesPresenter prompts = new PromptProfilesPresenter(
                            AiSubtitleData.instance(getContext()).prompts());
                    PromptProfilesPresenter.SaveResult result = update
                            ? prompts.update(editor.getId(), editor.getName(), editor.getContent())
                            : prompts.create(editor.getName(), editor.getContent());
                    if (result.isSuccess()) { AiSubtitleRuntime.applyToBridge(getContext()); showPromptProfiles(); }
                    return result.isSuccess();
                });
    }
}
