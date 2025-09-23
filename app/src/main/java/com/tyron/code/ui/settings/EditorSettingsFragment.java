package com.tyron.code.ui.settings;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.transition.MaterialSharedAxis;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.common.util.SingleTextWatcher;
import com.tyron.completion.progress.ProgressManager;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import org.eclipse.tm4e.core.internal.theme.raw.RawThemeReader;
import org.eclipse.tm4e.core.internal.theme.raw.IRawTheme;
import org.eclipse.tm4e.core.registry.IThemeSource;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;

import io.github.rosemoe.sora2.text.EditorUtil;
import java.io.File;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.codeassist.unofficial.R;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
 
 

public class EditorSettingsFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
    setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
    setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
    setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.editor_preferences, rootKey);

    EditTextPreference fontSize = findPreference(SharedPreferenceKeys.FONT_SIZE);
    if (fontSize != null) {
      fontSize.setOnBindEditTextListener(
          editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER));
    }

    Preference scheme = findPreference(SharedPreferenceKeys.SCHEME);
    assert scheme != null;
    scheme.setOnPreferenceClickListener(
        preference -> {
          SharedPreferences pref = preference.getSharedPreferences();
          String path = pref.getString("scheme", "");
          File currentTheme = new File(path);

          AlertDialog dialog =
              new MaterialAlertDialogBuilder(requireContext())
                  .setView(R.layout.base_textinput_layout)
                  .setTitle(R.string.change_scheme_dialog_title)
                  .setNeutralButton(
                      R.string.defaultString,
                      (d, w) -> {
                        pref.edit().putString(SharedPreferenceKeys.SCHEME, null).apply();
                        preference.callChangeListener(null);
                      })
                  .setNegativeButton(android.R.string.cancel, null)
                  .setPositiveButton(R.string.save, null)
                  .create();
          dialog.setOnShowListener(
              d -> {
                final Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);

                TextInputLayout layout = dialog.findViewById(R.id.textinput_layout);
                layout.setHint(R.string.set_scheme_file_path);
                EditText editText = Objects.requireNonNull(layout).getEditText();
                assert editText != null;

                editText.setText(currentTheme.getAbsolutePath());
                editText.addTextChangedListener(
                    new SingleTextWatcher() {
                      @Override
                      public void afterTextChanged(Editable editable) {
                        File file = new File(editable.toString());
                        boolean enabled = file.exists() && file.canRead() && file.isFile();
                        button.setEnabled(enabled);
                      }
                    });

                button.setOnClickListener(
                    v -> {
                      File file = new File(editText.getText().toString());
                      ListenableFuture<TextMateColorScheme> future = getColorScheme(file);
                      Futures.addCallback(
                          future,
                          new FutureCallback<TextMateColorScheme>() {
                            @Override
                            public void onSuccess(@Nullable TextMateColorScheme result) {
                              pref.edit()
                                  .putString(SharedPreferenceKeys.SCHEME, file.getAbsolutePath())
                                  .apply();
                              preference.callChangeListener(file.getAbsolutePath());
                              d.dismiss();
                            }

                            @Override
                            public void onFailure(@NonNull Throwable t) {
                              d.dismiss();
                              new MaterialAlertDialogBuilder(requireContext())
                                  .setTitle(R.string.error)
                                  .setMessage(t.getMessage())
                                  .setPositiveButton(android.R.string.ok, null)
                                  .setNegativeButton(android.R.string.cancel, null)
                                  .show();
                            }
                          },
                          ContextCompat.getMainExecutor(requireContext()));
                    });
              });
          dialog.show();
          return true;
        });
  }

  public static ListenableFuture<TextMateColorScheme> getColorScheme(@NonNull File file) {
    return ProgressManager.getInstance()
        .computeNonCancelableAsync(
            () -> {

             SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
            String selectedTheme = preferences.getString("theme", "default");
             
               String path = file.getAbsolutePath();
            
            ThemeRegistry themeRegistry = ThemeRegistry.getInstance();
            String name = path; // name of theme
            String themeAssetsPath = path;
           ThemeModel model = new ThemeModel(
               IThemeSource.fromInputStream(
              FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath), themeAssetsPath, null
             ), 
             name
           );
// If the theme is dark
 model.setDark(selectedTheme.equals("dark"));
try{             
model.load();
ThemeRegistry.getInstance().loadTheme(model);
}catch(Exception e){}
             
              return Futures.immediateFuture(EditorUtil.createTheme(/*themeModel*/));
            });
  }
}
