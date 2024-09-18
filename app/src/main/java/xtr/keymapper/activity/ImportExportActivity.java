package xtr.keymapper.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import xtr.keymapper.R;
import xtr.keymapper.databinding.ActivityImportExportBinding;
import xtr.keymapper.databinding.ProfileRowItem2Binding;
import xtr.keymapper.keymap.KeymapProfile;
import xtr.keymapper.keymap.KeymapProfiles;

public class ImportExportActivity extends AppCompatActivity {
    private static final int WRITE_REQUEST_CODE = 101;
    private static final int READ_REQUEST_CODE = 102;
    private ByteArrayOutputStream byteArrayOutputStream = null;
    private ActivityImportExportBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImportExportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.profiles.setAdapter(new ProfilesViewAdapter(this, false));

        binding.selectAllButton.setOnClickListener(v -> binding.profiles.setAdapter(new ProfilesViewAdapter(this, true)));
        binding.importButton.setOnClickListener(view -> openZipFile());
    }

    private void openZipFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, READ_REQUEST_CODE);
    }


    private void exportProfiles(ArrayList<String> profileNames) {
        if (profileNames.isEmpty()) return;
        KeymapProfiles keymapProfiles = new KeymapProfiles(this);
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
            profileNames.forEach(profileName -> {
                ZipEntry zipEntry = new ZipEntry(profileName);
                try {
                    zipOutputStream.putNextEntry(zipEntry);
                    keymapProfiles.sharedPref.getStringSet(profileName, new HashSet<>()).forEach(s -> {
                        try {
                            zipOutputStream.write(s.getBytes());
                            zipOutputStream.write("\n".getBytes());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    zipOutputStream.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            zipOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.putExtra(Intent.EXTRA_TITLE, "backup.zip");
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, WRITE_REQUEST_CODE);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WRITE_REQUEST_CODE && resultCode == RESULT_OK) {
                    if (data != null
                            && data.getData() != null
                                && byteArrayOutputStream != null) {
                        try ( OutputStream outputStream =
                                      getContentResolver().openOutputStream(data.getData())) {
                            outputStream.write(byteArrayOutputStream.toByteArray());
                            outputStream.close();
                            byteArrayOutputStream = null;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

        } else if (requestCode == READ_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null
                    && data.getData() != null) {
                importProfiles(data.getData());
            }
        }
    }

    private void importProfiles(Uri dataUri) {
        try (InputStream inputStream =
                     getContentResolver().openInputStream(dataUri)) {
            ZipInputStream zipInputStream = new ZipInputStream(inputStream);
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                String profileName = zipEntry.getName();
                Set<String> stringSet = new HashSet<>();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(zipInputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    stringSet.add(line);
                }
                new KeymapProfiles(this).sharedPref
                        .edit()
                        .putStringSet(profileName, stringSet)
                        .apply();
                zipInputStream.closeEntry();
            }
            zipInputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Refresh RecyclerView
        binding.profiles.setAdapter(new ProfilesViewAdapter(this, false));
    }

    class ProfilesViewAdapter extends RecyclerView.Adapter<ProfilesViewAdapter.ViewHolder> {

        private final ArrayList<RecyclerData> recyclerDataArrayList = new ArrayList<>();
        private final ArrayList<String> profileNames = new ArrayList<>();
        private final boolean allCardsChecked;


        /**
         * Provide a reference to the type of views used
         */
        public class ViewHolder extends RecyclerView.ViewHolder {

            private final ProfileRowItem2Binding binding;

            public ViewHolder(ProfileRowItem2Binding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        /**
         * Initialize the dataset of the Adapter.
         */
        public ProfilesViewAdapter(Context context, boolean allCardsChecked) {
            this.allCardsChecked = allCardsChecked;
            KeymapProfiles keymapProfiles = new KeymapProfiles(context);

            new KeymapProfiles(context).getAllProfiles().forEach((profileName, profile) -> {
                if(profileName != null) {
                    recyclerDataArrayList.add(new RecyclerData(profile, context, profileName));
                    if (allCardsChecked) profileNames.add(profileName);
                } else {
                    keymapProfiles.deleteProfile(null);
                }
            });
            binding.exportButton.setOnClickListener(view -> exportProfiles(profileNames));
        }
        // Create new views (invoked by the layout manager)
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
            // Create a new view
            ProfileRowItem2Binding itemBinding = ProfileRowItem2Binding.inflate(LayoutInflater.from(viewGroup.getContext()), viewGroup, false);
            return new ViewHolder(itemBinding);
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, final int position) {
            // Get element from dataset at this position and set the contents of the view
            RecyclerData recyclerData = recyclerDataArrayList.get(position);
            viewHolder.binding.appIcon.setImageDrawable(recyclerData.icon);
            viewHolder.binding.profileName.setText(recyclerData.name);
            viewHolder.binding.profileText.setText(recyclerData.description);
            viewHolder.binding.card.setChecked(allCardsChecked);
            viewHolder.binding.card.setOnClickListener(v -> {
                viewHolder.binding.card.setChecked(!viewHolder.binding.card.isChecked());
                if (viewHolder.binding.card.isChecked()) profileNames.add(recyclerData.name);
                else profileNames.remove(recyclerData.name);
            });
        }

        // Return the size of dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            return recyclerDataArrayList.size();
        }

        private class RecyclerData {
            public RecyclerData(KeymapProfile profile, Context context, String name) {
                this.name = name;
                this.description = new KeymapProfiles(context).sharedPref.getStringSet(name, new HashSet<>()).toString();
                try {
                    this.icon = context.getPackageManager().getApplicationIcon(profile.packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    this.icon = AppCompatResources.getDrawable(context, R.mipmap.ic_launcher_foreground);
                }
            }

            String description;
            String name;
            Drawable icon;
        }
    }

}