package org.dropbox;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.FileCategory;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderErrorException;
import com.dropbox.core.v2.files.SearchOptions;
import com.dropbox.core.v2.files.SearchV2Result;
import com.dropbox.core.v2.files.UploadErrorException;
import com.dropbox.core.v2.sharing.AccessLevel;
import com.dropbox.core.v2.sharing.AclUpdatePolicy;
import com.dropbox.core.v2.sharing.AddMember;
import com.dropbox.core.v2.sharing.MemberSelector;
import com.dropbox.core.v2.sharing.ShareFolderLaunch;
import depskyDep.IDepSkySDriver;
import exceptions.StorageCloudException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DropboxAccessDriver implements IDepSkySDriver {

    private static final String defaultNamespace = "depsky";
    private DbxClientV2 client;
    private String accessKey;

    private String secretKey;

    private String driverId;

    public DropboxAccessDriver(String driverId, String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.driverId = driverId;
    }


    @Override
    public String uploadData(String bucket, byte[] data, String fileId, String[] uploadToAnotherAccount)
            throws StorageCloudException {
        String folder = !this.isEmpty(bucket) ? bucket : defaultNamespace;

        try (InputStream in = new ByteArrayInputStream(data)) {
            FileMetadata metadata = client.files().uploadBuilder("/" + folder + "/" + fileId).uploadAndFinish(in);

        } catch (UploadErrorException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        } catch (IOException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        } catch (DbxException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        }

        return fileId;
    }

    @Override
    public byte[] downloadData(String bucket, String id, String[] uploadToAnotherAccount) throws StorageCloudException {
        FileMetadata metadata;

        String folder = !this.isEmpty(bucket) ? bucket : defaultNamespace;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            metadata = client.files().downloadBuilder("/" + folder + "/" + id).download(out);
        } catch (DbxException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        } catch (IOException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        }

        return out.toByteArray();
    }

    @Override
    public boolean deleteData(String bucket, String id, String[] uploadToAnotherAccount) throws StorageCloudException {
        String folder = !this.isEmpty(bucket) ? bucket : defaultNamespace;

        try {
            client.files().deleteV2("/" + folder + "/" + id);
        } catch (DeleteErrorException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        } catch (DbxException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        }

        return false;
    }

    @Override
    public boolean deleteContainer(String bucket, String[] allNames, String[] uploadToAnotherAccount)
            throws StorageCloudException {
        return false;
    }

    @Override
    public String initSession() throws StorageCloudException {

        DbxRequestConfig config = new DbxRequestConfig("Depsky/1.1");
        client = new DbxClientV2(config, accessKey);
        return "sid";
    }

    @Override
    public LinkedList<String> listNames(String prefix, String bucket, String[] uploadToAnotherAccount)
            throws StorageCloudException {

        String folder = !this.isEmpty(bucket) ? bucket : defaultNamespace;

        try {
            LinkedList<String> filesList = new LinkedList<>();
            client.files().listFolder("/" + folder).getEntries().forEach(entry -> {
                filesList.add(entry.getName());
            });

            return filesList;
        } catch (ListFolderErrorException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        } catch (DbxException e) {
            throw new StorageCloudException("DropboxException::" + e.getMessage());
        }
    }

    @Override
    public LinkedList<String> listDataUnits() {
        return null;
    }

    @Override
    public boolean endSession(String sid) throws StorageCloudException {
        return false;
    }

    @Override
    public String getDriverId() {
        return driverId;
    }

    @Override
    public String[] setAcl(String bucket, String[] canonicalId, String permission) throws StorageCloudException {
        boolean withRead = false;

        try {
            SearchOptions options = SearchOptions.newBuilder().withFileCategories(List.of(FileCategory.FOLDER)).build();
            SearchV2Result result = client.files().searchV2Builder(bucket).withOptions(options).start();

            if (result.getMatches().isEmpty()) {
                return null;
            }

            result.getMatches().get(0).getMetadata();

            ShareFolderLaunch folderDetails = client.sharing().shareFolderBuilder("/" + bucket)
                    .withAclUpdatePolicy(AclUpdatePolicy.OWNER).start();

            AccessLevel accessLevel = switch (permission) {
                case "r" -> AccessLevel.VIEWER;
                case "rw", "w" -> AccessLevel.EDITOR;
                default -> throw new StorageCloudException("Invalid permission: " + permission);
            };

            List<AddMember> newMembers = new ArrayList<>();

            for (String email : canonicalId) {
                MemberSelector member = MemberSelector.email(email);
                newMembers.add(new AddMember(member, accessLevel));
            }

            client.sharing().addFolderMember(folderDetails.getCompleteValue().getSharedFolderId(), newMembers);

        } catch (DbxException e) {
            throw new RuntimeException(e);
        }


        return new String[0];
    }

    private boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
}