package com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.ui;

import android.content.Context;

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
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.AiSubtitleData;
import com.liskovsoft.smartyoutubetv2.common.ai.subtitle.settings.SecretStore;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.util.ArrayList;
import java.util.List;

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
            ProviderPreset preset = ProviderPreset.forType(type);
            types.add(UiOptionItem.from(preset.getDisplayName(),
                    option -> showCreateName(type, preset.getProtocol())));
        }

        presenter.appendRadioCategory(
                getContext().getString(R.string.ai_subtitle_choose_provider_type), types);
        presenter.showDialog(getContext().getString(R.string.ai_subtitle_add_profile));
    }

    private void showCreateName(final ProviderType type, final ProviderProtocol protocol) {
        final ProviderProfileEditor editor = ProviderProfileEditor.create(type, protocol);

        SimpleEditDialog.show(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_name),
                "", newValue -> {
                    editor.setName(newValue);
                    showCreateBaseUrl(editor);
                    return true;
                });
    }

    private void showCreateBaseUrl(final ProviderProfileEditor editor) {
        SimpleEditDialog.show(getContext(),
                getContext().getString(R.string.ai_subtitle_profile_base_url),
                "", newValue -> {
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

        presenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.ai_subtitle_cancel),
                option -> {
                    if (handle[0] != null) {
                        handle[0].cancel();
                    }
                    presenter.closeDialog();
                }));

        handle[0] = profilesPresenter().testConnection(profile,
                new ProviderProfilesPresenter.ConnectionTestListener() {
                    @Override
                    public void onStarted() {
                        presenter.showDialog(getContext().getString(
                                R.string.ai_subtitle_test_connection_running));
                    }

                    @Override
                    public void onResult(ConnectionTestResult result) {
                        presenter.closeDialog();
                        MessageHelpers.showMessage(getContext(), result.isSuccess()
                                ? getContext().getString(
                                        R.string.ai_subtitle_test_connection_success)
                                : getContext().getString(
                                        R.string.ai_subtitle_test_connection_failed));
                    }
                });
    }

    private ProviderProfilesPresenter profilesPresenter() {
        AiSubtitleData data = AiSubtitleData.instance(getContext());
        SecretStore secrets = data.secrets();
        ProviderProfileResolver resolver =
                new ProviderProfileResolver(new OkHttpRequestExecutor(), secrets);
        return new ProviderProfilesPresenter(data.providerProfiles(), secrets, resolver,
                new ModelCatalog(new OkHttpRequestExecutor()));
    }
}