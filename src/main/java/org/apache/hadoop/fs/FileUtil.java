//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.apache.hadoop.fs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.nativeio.NativeIO;
import org.apache.hadoop.util.Shell;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Shell.ShellCommandExecutor;

public class FileUtil {
    private static final Log LOG = LogFactory.getLog(FileUtil.class);

    public FileUtil() {
    }

    public static Path[] stat2Paths(FileStatus[] stats) {
        if(stats == null) {
            return null;
        } else {
            Path[] ret = new Path[stats.length];

            for(int i = 0; i < stats.length; ++i) {
                ret[i] = stats[i].getPath();
            }

            return ret;
        }
    }

    public static Path[] stat2Paths(FileStatus[] stats, Path path) {
        return stats == null?new Path[]{path}:stat2Paths(stats);
    }

    public static boolean fullyDelete(File dir) throws IOException {
        return !fullyDeleteContents(dir)?false:dir.delete();
    }

    public static boolean fullyDeleteContents(File dir) throws IOException {
        boolean deletionSucceeded = true;
        File[] contents = dir.listFiles();
        if(contents != null) {
            for(int i = 0; i < contents.length; ++i) {
                if(contents[i].isFile()) {
                    if(!contents[i].delete()) {
                        deletionSucceeded = false;
                    }
                } else {
                    boolean b = false;
                    b = contents[i].delete();
                    if(!b && !fullyDelete(contents[i])) {
                        deletionSucceeded = false;
                    }
                }
            }
        }

        return deletionSucceeded;
    }

    /** @deprecated */
    @Deprecated
    public static void fullyDelete(FileSystem fs, Path dir) throws IOException {
        fs.delete(dir, true);
    }

    private static void checkDependencies(FileSystem srcFS, Path src, FileSystem dstFS, Path dst) throws IOException {
        if(srcFS == dstFS) {
            String srcq = src.makeQualified(srcFS).toString() + "/";
            String dstq = dst.makeQualified(dstFS).toString() + "/";
            if(dstq.startsWith(srcq)) {
                if(srcq.length() == dstq.length()) {
                    throw new IOException("Cannot copy " + src + " to itself.");
                }

                throw new IOException("Cannot copy " + src + " to its subdirectory " + dst);
            }
        }

    }

    public static boolean copy(FileSystem srcFS, Path src, FileSystem dstFS, Path dst, boolean deleteSource, Configuration conf) throws IOException {
        return copy(srcFS, src, dstFS, dst, deleteSource, true, conf);
    }

    public static boolean copy(FileSystem srcFS, Path[] srcs, FileSystem dstFS, Path dst, boolean deleteSource, boolean overwrite, Configuration conf) throws IOException {
        boolean gotException = false;
        boolean returnVal = true;
        StringBuffer exceptions = new StringBuffer();
        if(srcs.length == 1) {
            return copy(srcFS, srcs[0], dstFS, dst, deleteSource, overwrite, conf);
        } else if(!dstFS.exists(dst)) {
            throw new IOException("`" + dst + "\': specified destination directory " + "does not exist");
        } else {
            FileStatus arr$ = dstFS.getFileStatus(dst);
            if(!arr$.isDir()) {
                throw new IOException("copying multiple files, but last argument `" + dst + "\' is not a directory");
            } else {
                Path[] var16 = srcs;
                int len$ = srcs.length;

                for(int i$ = 0; i$ < len$; ++i$) {
                    Path src = var16[i$];

                    try {
                        if(!copy(srcFS, src, dstFS, dst, deleteSource, overwrite, conf)) {
                            returnVal = false;
                        }
                    } catch (IOException var15) {
                        gotException = true;
                        exceptions.append(var15.getMessage());
                        exceptions.append("\n");
                    }
                }

                if(gotException) {
                    throw new IOException(exceptions.toString());
                } else {
                    return returnVal;
                }
            }
        }
    }

    public static boolean copy(FileSystem srcFS, Path src, FileSystem dstFS, Path dst, boolean deleteSource, boolean overwrite, Configuration conf) throws IOException {
        dst = checkDest(src.getName(), dstFS, dst, overwrite);
        FileStatus[] in;
        if(srcFS.getFileStatus(src).isDir()) {
            checkDependencies(srcFS, src, dstFS, dst);
            if(!dstFS.mkdirs(dst)) {
                return false;
            }

            in = srcFS.listStatus(src);

            for(int out = 0; out < in.length; ++out) {
                copy(srcFS, in[out].getPath(), dstFS, new Path(dst, in[out].getPath().getName()), deleteSource, overwrite, conf);
            }
        } else {
            if(!srcFS.isFile(src)) {
                throw new IOException(src.toString() + ": No such file or directory");
            }

            in = null;
            FSDataOutputStream var12 = null;

            try {
                FSDataInputStream var11 = srcFS.open(src);
                var12 = dstFS.create(dst, overwrite);
                IOUtils.copyBytes(var11, var12, conf, true);
            } catch (IOException var10) {
                IOUtils.closeStream(var12);
//                IOUtils.closeStream(in);
                throw var10;
            }
        }

        return deleteSource?srcFS.delete(src, true):true;
    }

    public static boolean copyMerge(FileSystem srcFS, Path srcDir, FileSystem dstFS, Path dstFile, boolean deleteSource, Configuration conf, String addString) throws IOException {
        dstFile = checkDest(srcDir.getName(), dstFS, dstFile, false);
        if(!srcFS.getFileStatus(srcDir).isDir()) {
            return false;
        } else {
            FSDataOutputStream out = dstFS.create(dstFile);

            try {
                FileStatus[] contents = srcFS.listStatus(srcDir);

                for(int i = 0; i < contents.length; ++i) {
                    if(!contents[i].isDir()) {
                        FSDataInputStream in = srcFS.open(contents[i].getPath());

                        try {
                            IOUtils.copyBytes(in, out, conf, false);
                            if(addString != null) {
                                out.write(addString.getBytes("UTF-8"));
                            }
                        } finally {
                            in.close();
                        }
                    }
                }
            } finally {
                out.close();
            }

            return deleteSource?srcFS.delete(srcDir, true):true;
        }
    }

    public static boolean copy(File src, FileSystem dstFS, Path dst, boolean deleteSource, Configuration conf) throws IOException {
        dst = checkDest(src.getName(), dstFS, dst, false);
        File[] in;
        if(src.isDirectory()) {
            if(!dstFS.mkdirs(dst)) {
                return false;
            }

            in = listFiles(src);

            for(int out = 0; out < in.length; ++out) {
                copy(in[out], dstFS, new Path(dst, in[out].getName()), deleteSource, conf);
            }
        } else {
            if(!src.isFile()) {
                throw new IOException(src.toString() + ": No such file or directory");
            }

            in = null;
            FSDataOutputStream var10 = null;

            try {
                FileInputStream var9 = new FileInputStream(src);
                var10 = dstFS.create(dst);
                IOUtils.copyBytes(var9, var10, conf);
            } catch (IOException var8) {
                IOUtils.closeStream(var10);
//                IOUtils.closeStream(in);
                throw var8;
            }
        }

        return deleteSource?fullyDelete(src):true;
    }

    public static boolean copy(FileSystem srcFS, Path src, File dst, boolean deleteSource, Configuration conf) throws IOException {
        if(srcFS.getFileStatus(src).isDir()) {
            if(!dst.mkdirs()) {
                return false;
            }

            FileStatus[] in = srcFS.listStatus(src);

            for(int i = 0; i < in.length; ++i) {
                copy(srcFS, in[i].getPath(), new File(dst, in[i].getPath().getName()), deleteSource, conf);
            }
        } else {
            if(!srcFS.isFile(src)) {
                throw new IOException(src.toString() + ": No such file or directory");
            }

            FSDataInputStream var7 = srcFS.open(src);
            IOUtils.copyBytes(var7, new FileOutputStream(dst), conf);
        }

        return deleteSource?srcFS.delete(src, true):true;
    }

    private static Path checkDest(String srcName, FileSystem dstFS, Path dst, boolean overwrite) throws IOException {
        if(dstFS.exists(dst)) {
            FileStatus sdst = dstFS.getFileStatus(dst);
            if(sdst.isDir()) {
                if(null == srcName) {
                    throw new IOException("Target " + dst + " is a directory");
                }

                return checkDest((String)null, dstFS, new Path(dst, srcName), overwrite);
            }

            if(!overwrite) {
                throw new IOException("Target " + dst + " already exists");
            }
        } else if(dst.toString().isEmpty()) {
            return checkDest((String)null, dstFS, new Path(srcName), overwrite);
        }

        return dst;
    }

    public static String makeShellPath(String filename) throws IOException {
        return Path.WINDOWS?(new FileUtil.CygPathCommand(filename)).getResult():filename;
    }

    public static String makeShellPath(File file) throws IOException {
        return makeShellPath(file, false);
    }

    public static String makeShellPath(File file, boolean makeCanonicalPath) throws IOException {
        return makeCanonicalPath?makeShellPath(file.getCanonicalPath()):makeShellPath(file.toString());
    }

    public static long getDU(File dir) {
        long size = 0L;
        if(!dir.exists()) {
            return 0L;
        } else if(!dir.isDirectory()) {
            return dir.length();
        } else {
            File[] allFiles = dir.listFiles();
            if(allFiles != null) {
                for(int i = 0; i < allFiles.length; ++i) {
                    boolean isSymLink;
                    try {
                        isSymLink = FileUtils.isSymlink(allFiles[i]);
                    } catch (IOException var7) {
                        isSymLink = true;
                    }

                    if(!isSymLink) {
                        size += getDU(allFiles[i]);
                    }
                }
            }

            return size;
        }
    }

    public static void unZip(File inFile, File unzipDir) throws IOException {
        ZipFile zipFile = new ZipFile(inFile);

        try {
            Enumeration entries = zipFile.entries();

            while(entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry)entries.nextElement();
                if(!entry.isDirectory()) {
                    InputStream in = zipFile.getInputStream(entry);

                    try {
                        File file = new File(unzipDir, entry.getName());
                        if(!file.getParentFile().mkdirs() && !file.getParentFile().isDirectory()) {
                            throw new IOException("Mkdirs failed to create " + file.getParentFile().toString());
                        }

                        FileOutputStream out = new FileOutputStream(file);

                        try {
                            byte[] buffer = new byte[8192];

                            int i;
                            while((i = in.read(buffer)) != -1) {
                                out.write(buffer, 0, i);
                            }
                        } finally {
                            out.close();
                        }
                    } finally {
                        in.close();
                    }
                }
            }
        } finally {
            zipFile.close();
        }

    }

    public static void unTar(File inFile, File untarDir) throws IOException {
        if(!untarDir.mkdirs() && !untarDir.isDirectory()) {
            throw new IOException("Mkdirs failed to create " + untarDir);
        } else {
            StringBuffer untarCommand = new StringBuffer();
            boolean gzipped = inFile.toString().endsWith("gz");
            if(gzipped) {
                untarCommand.append(" gzip -dc \'");
                untarCommand.append(makeShellPath(inFile));
                untarCommand.append("\' | (");
            }

            untarCommand.append("cd \'");
            untarCommand.append(makeShellPath(untarDir));
            untarCommand.append("\' ; ");
            untarCommand.append("tar -xf ");
            if(gzipped) {
                untarCommand.append(" -)");
            } else {
                untarCommand.append(makeShellPath(inFile));
            }

            String[] shellCmd = new String[]{"bash", "-c", untarCommand.toString()};
            ShellCommandExecutor shexec = new ShellCommandExecutor(shellCmd);
            shexec.execute();
            int exitcode = shexec.getExitCode();
            if(exitcode != 0) {
                throw new IOException("Error untarring file " + inFile + ". Tar process exited with exit code " + exitcode);
            }
        }
    }

    public static int symLink(String target, String linkname) throws IOException {
        String cmd = "ln -s " + target + " " + linkname;
        Process p = Runtime.getRuntime().exec(cmd, (String[])null);
        int returnVal = -1;

        try {
            returnVal = p.waitFor();
        } catch (InterruptedException var6) {
            ;
        }

        if(returnVal != 0) {
            LOG.warn("Command \'" + cmd + "\' failed " + returnVal + " with: " + copyStderr(p));
        }

        return returnVal;
    }

    private static String copyStderr(Process p) throws IOException {
        InputStream err = p.getErrorStream();
        StringBuilder result = new StringBuilder();
        byte[] buff = new byte[4096];

        for(int len = err.read(buff); len > 0; len = err.read(buff)) {
            result.append(new String(buff, 0, len));
        }

        return result.toString();
    }

    public static int chmod(String filename, String perm) throws IOException, InterruptedException {
        return chmod(filename, perm, false);
    }

    public static int chmod(String filename, String perm, boolean recursive) throws IOException {
        StringBuffer cmdBuf = new StringBuffer();
        cmdBuf.append("chmod ");
        if(recursive) {
            cmdBuf.append("-R ");
        }

        cmdBuf.append(perm).append(" ");
        cmdBuf.append(filename);
        String[] shellCmd = new String[]{"bash", "-c", cmdBuf.toString()};
        ShellCommandExecutor shExec = new ShellCommandExecutor(shellCmd);

        try {
            shExec.execute();
        } catch (IOException var7) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Error while changing permission : " + filename + " Exception: " + StringUtils.stringifyException(var7));
            }
        }

        return shExec.getExitCode();
    }

    public static void setPermission(File f, FsPermission permission) throws IOException {
        FsAction user = permission.getUserAction();
        FsAction group = permission.getGroupAction();
        FsAction other = permission.getOtherAction();
        if(group == other && !NativeIO.isAvailable()) {
            boolean rv = true;
            rv = f.setReadable(group.implies(FsAction.READ), false);
            checkReturnValue(rv, f, permission);
            if(group.implies(FsAction.READ) != user.implies(FsAction.READ)) {
                f.setReadable(user.implies(FsAction.READ), true);
                checkReturnValue(rv, f, permission);
            }

            rv = f.setWritable(group.implies(FsAction.WRITE), false);
            checkReturnValue(rv, f, permission);
            if(group.implies(FsAction.WRITE) != user.implies(FsAction.WRITE)) {
                f.setWritable(user.implies(FsAction.WRITE), true);
                checkReturnValue(rv, f, permission);
            }

            rv = f.setExecutable(group.implies(FsAction.EXECUTE), false);
            checkReturnValue(rv, f, permission);
            if(group.implies(FsAction.EXECUTE) != user.implies(FsAction.EXECUTE)) {
                f.setExecutable(user.implies(FsAction.EXECUTE), true);
                checkReturnValue(rv, f, permission);
            }

        } else {
            execSetPermission(f, permission);
        }
    }

    private static void checkReturnValue(boolean rv, File p, FsPermission permission) throws IOException {
//        if(!rv) {
//            throw new IOException("Failed to set permissions of path: " + p + " to " + String.format("%04o", new Object[]{Short.valueOf(permission.toShort())}));
//        }
    }

    private static void execSetPermission(File f, FsPermission permission) throws IOException {
        if(NativeIO.isAvailable()) {
            NativeIO.chmod(f.getCanonicalPath(), permission.toShort());
        } else {
            execCommand(f, new String[]{"chmod", String.format("%04o", new Object[]{Short.valueOf(permission.toShort())})});
        }

    }

    static String execCommand(File f, String... cmd) throws IOException {
        String[] args = new String[cmd.length + 1];
        System.arraycopy(cmd, 0, args, 0, cmd.length);
        args[cmd.length] = f.getCanonicalPath();
        String output = Shell.execCommand(args);
        return output;
    }

    public static final File createLocalTempFile(File basefile, String prefix, boolean isDeleteOnExit) throws IOException {
        File tmp = File.createTempFile(prefix + basefile.getName(), "", basefile.getParentFile());
        if(isDeleteOnExit) {
            tmp.deleteOnExit();
        }

        return tmp;
    }

    public static void replaceFile(File src, File target) throws IOException {
        if(!src.renameTo(target)) {
            int retries = 5;

            while(target.exists() && !target.delete() && retries-- >= 0) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException var4) {
                    throw new IOException("replaceFile interrupted.");
                }
            }

            if(!src.renameTo(target)) {
                throw new IOException("Unable to rename " + src + " to " + target);
            }
        }

    }

    public static File[] listFiles(File dir) throws IOException {
        File[] files = dir.listFiles();
        if(files == null) {
            throw new IOException("Invalid directory or I/O error occurred for dir: " + dir.toString());
        } else {
            return files;
        }
    }

    public static String[] list(File dir) throws IOException {
        String[] fileNames = dir.list();
        if(fileNames == null) {
            throw new IOException("Invalid directory or I/O error occurred for dir: " + dir.toString());
        } else {
            return fileNames;
        }
    }

    private static class CygPathCommand extends Shell {
        String[] command;
        String result;

        CygPathCommand(String path) throws IOException {
            this.command = new String[]{"cygpath", "-u", path};
            this.run();
        }

        String getResult() throws IOException {
            return this.result;
        }

        protected String[] getExecString() {
            return this.command;
        }

        protected void parseExecResult(BufferedReader lines) throws IOException {
            String line = lines.readLine();
            if(line == null) {
                throw new IOException("Can\'t convert \'" + this.command[2] + " to a cygwin path");
            } else {
                this.result = line;
            }
        }
    }
}
