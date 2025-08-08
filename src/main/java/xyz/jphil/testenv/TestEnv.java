package xyz.jphil.testenv;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public class TestEnv {
    private final Path artifactRoot;
    
    /**
     * Create a TestEnv instance based on the given class location
     * @param clazz The class to use as reference for path discovery
     */
    public TestEnv(Class<?> clazz) {
        try {
            Path classPath = getClassPath(clazz);
            this.artifactRoot = findArtifactRoot(classPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TestEnv paths for class: " + clazz.getName(), e);
        }
    }
    
    /**
     * Get the path to the class file (works for both compiled classes and in IDE)
     */
    private static Path getClassPath(Class<?> clazz) throws Exception {
        // Try to get the class location
        ProtectionDomain protectionDomain = clazz.getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();
        
        if (codeSource != null) {
            URL location = codeSource.getLocation();
            URI uri = location.toURI();
            Path path = Paths.get(uri);
            
            // If it's a JAR file, get its parent directory
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                return path.getParent();
            }
            
            // If it points to classes directory, that's our starting point
            return path;
        }
        
        // Fallback: try to get resource path
        String className = clazz.getName().replace('.', '/') + ".class";
        URL resource = clazz.getClassLoader().getResource(className);
        if (resource != null) {
            URI uri = resource.toURI();
            String uriString = uri.toString();
            
            // Remove the class path part to get to the root
            if (uriString.startsWith("file:")) {
                String filePath = uriString.substring(5); // Remove "file:"
                // Remove the package structure and class file
                int classIndex = filePath.indexOf(className);
                if (classIndex > 0) {
                    filePath = filePath.substring(0, classIndex);
                }
                return Paths.get(filePath);
            }
        }
        
        throw new RuntimeException("Could not determine class path for: " + clazz.getName());
    }
    
    /**
     * Find the module root by looking for pom.xml walking up the directory tree
     */
    private static Path findArtifactRoot(Path startPath) {
        Path current = startPath;
        
        // Walk up the directory tree looking for pom.xml
        while (current != null) {
            Path pomFile = current.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                return current;
            }
            current = current.getParent();
        }
        
        throw new RuntimeException("Could not find module root (pom.xml) starting from: " + startPath);
    }
    
    
    /**
     * Get the module/project root path
     */
    public Path relFromArtifact(String ... relPath) {
        return relativize(artifactRoot, relPath);
    }
    
    /**
     * Get the super project root path
     */
    public Path relFromArtifactParent(String ... relPath) {
        return relativize(artifactRoot.getParent(), relPath);
    }
    
    public static Path relativize(Path srcPath, String ... relPath){
        if(relPath==null || relPath.length==0)
            return srcPath;
        var retPth = srcPath;
        for (var p : relPath) {
            retPth = retPth.resolve(p);
        }
        return retPth;
    }
    
    /**
     * Create a path from string components
     */
    public Path path(String first, String... more) {
        return Paths.get(first, more);
    }
    
    
    // Static utility methods that don't depend on instance
    
    /**
     * Get user home directory
     */
    public static Path userhome() {
        return Paths.get(System.getProperty("user.home"));
    }
    
    /**
     * Get current OS type for debugging
     */
    public static String getOSInfo() {
        return String.format("OS: %s, WSL: %s", 
                System.getProperty("os.name"),
                isWSL());
    }
    
    /**
     * Check if running in WSL
     */
    public static boolean isWSL() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("linux") && 
               (System.getenv("WSL_DISTRO_NAME") != null || 
                System.getenv("WSLENV") != null);
    }
    
    /**
     * Debug method to print discovered paths
     */
    public void debugPaths() {
        System.out.println("=== TestEnv Debug Info ===");
        System.out.println("OS Info: " + getOSInfo());
        System.out.println("Artifact Root: " + artifactRoot);
        System.out.println("User Home: " + userhome());
        System.out.println("========================");
    }
    
    @Override
    public String toString() {
        return String.format("TestEnv{moduleRoot='%s'}", artifactRoot);
    }
}