/**
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 */
package gov.nist.oar.distrib.web;

import gov.nist.oar.distrib.LongTermStorage;
import gov.nist.oar.distrib.storage.AWSS3LongTermStorage;
import gov.nist.oar.distrib.storage.FilesystemLongTermStorage;
import gov.nist.oar.distrib.service.FileDownloadService;
import gov.nist.oar.distrib.service.NerdmDrivenFromBagFileDownloadService;
import gov.nist.oar.distrib.service.PreservationBagService;
import gov.nist.oar.distrib.service.DefaultPreservationBagService;
import gov.nist.oar.distrib.service.DataPackagingService;
import gov.nist.oar.distrib.service.DefaultDataPackagingService;

import java.io.InputStream;
import java.io.FileNotFoundException;
import javax.activation.MimetypesFileTypeMap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.util.UrlPathHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.RegionUtils;
    

/**
 * The configuration for the Distribution Service used in the NIST Public Data Repository.
 * <p>
 * The class defines how the components are put together to form the distribution web service
 * using the Spring Boot framework.  The following configuration properties are supported for 
 * driving the configuration:
 * <dl>
 *   <dt> {@code distrib.bagstore.mode} </dt>
 *   <dd> a label that indicates how preservation bags are stored; the following values are 
 *        supported:
 *        <dl>
 *          <dd> <strong>local</strong> -- the bags are stored under a local directory (specified 
 *               by {@code distrib.bagstore.location}); the {@code FilesystemLongTermStorage} 
 *               class will be used to access the bags.  </dd>
 *          <dd> <strong>aws</strong> -- the bags are stored in an AWS S3 bucket (whose name
 *               is specified by {@code distrib.bagstore.location}), assuming the distribution
 *               service itself is running on an AWS EC2 machine.  The {@code AWSS3LongTermStorage} 
 *               class will be used to access the bags, and implicit credentials through the 
 *               EC2 machine will be used.  </dd>
 *          <dd> <strong>remote</strong> -- the bags are stored in an AWS S3 bucket (whose name
 *               is specified by {@code distrib.bagstore.location}), assuming the distribution
 *               service itself is running on an AWS EC2 machine.  The {@code AWSS3LongTermStorage} 
 *               class will be used to access the bags; however, credentials must be explicitly 
 *               provided. <em>Not yet implemented; will throw exception.</em> </dd>
 *        </dl> 
 *        All other values will throw an exception.  </dd>
 *   <dt> {@code distrib.bagstore.location} </dt>
 *   <dd> The location where the preservation bags are stored.  If {@code distrib.bagstore.mode}
 *        is set to {@code local}, this value is the path to a directory on the local filesystem.
 *        If {@code distrib.bagstore.mode} is set to {@code aws} or {@code remote}, this value is 
 *        the name of an S3 bucket. </dd>
 *   <dt> {@code distrib.baseurl} </dt>
 *   <dd> The base URL for the service (as seen by external, public users); this should include 
 *        everything up to but not including the "/ds" part of the path. </dd>
 *   <dt> {@code cloud.aws.region} </dt>
 *   <dd> The AWS service region (e.g. "us-east-1") to use.  This is ignored if 
 *        {@code distrib.bagstore.mode}=local. </dd>
 *   <dt> {@code distrib.packaging.maxfilecount} </dt>
 *   <dd> The maximum number of files that can be included within a single data package (see 
 *        {@link gov.nist.oar.distrib.service.DataPackagingService}) </dd>
 *   <dt> {@code distrib.packaging.maxpackagesize} </dt>
 *   <dd> The maximum file size for a single data package
 *        {@link gov.nist.oar.distrib.service.DataPackagingService}) </dd>
 *   <dt> {@code distrib.packaging.allowedurls} </dt>
 *   <dd> a pipe-delimited list of regular expressions for URLs that are allowed to appear in a data 
 *        packaging request (see
 *        {@link gov.nist.oar.distrib.service.DataPackagingService}) </dd>
 * </dl>
 * <p>
 * See also 
 * {@see gov.nist.oar.distrib.web.LocalstackDistribServiceConfig LocalstackDistribServiceConfig}
 */
@SpringBootApplication
public class NISTDistribServiceConfig {

    private static Logger logger = LoggerFactory.getLogger(NISTDistribServiceConfig.class);

    /**
     * the location where preservation bags can be found.  If <code>distrib.bagstore.mode</code> 
     * is "local",  this value is a directory path.  If the mode is "aws" or "remote", then this is
     * an S3 bucket name.  
     */
    @Value("${distrib.bagstore.location}")
    String bagstore;

    /**
     * the storage mode of which three are recognized:
     * <ul>
     *   <li> {@code aws} - Bags are stored in an S3 bucket and accessed from an EC2 machine 
     *                      (production mode); in this case, bagstore holds the S3 bucket name. </li>
     *   <li> {@code remote} - Bags are stored in an S3 bucket and accessed from a remote (non-EC2) 
     *                       machine (a development mode); in this case, bagstore holds the S3 bucket 
     *                       name. </li>
     *   <li> {@code local}  - Bags are stored under a directory on the local filesystem
     *                       (testing or development mode); in this case, bagstore identifies the 
     *                       bags' parent directory.  </li>
     */
    @Value("${distrib.bagstore.mode}")
    String mode;

    /**
     * the AWS region the service should operate in; this is ignored if mode=local.
     */
    @Value("${cloud.aws.region:@null}")
    String region;

    /**
     * the maximum allowed size of a data package.  A package will only exceed this size if it contains
     * a single file (that would not fit otherwise).
     */
    @Value("${distrib.packaging.maxpackagesize:500000000}")
    long maxPkgSize;
	
    /**
     * the maximum number of files to allow in a single data package
     */
    @Value("${distrib.packaging.maxfilecount:200}")
    int maxFileCount;
	
    /**
     * a white list of allowed URL patterns from which to retrieve data to include in data packages.
     * This value is given as a regular expression Strings delimited by pipe (|) characters; each 
     * expression represents a pattern for a URL that is allowed to appear in a data packaging request.
     * A URL can match any of the patterns to be considered allowed.  A pattern is matched against a 
     * URL starting with the authority field (i.e. just the "://") and may include parts of the URL 
     * path.  
     */
    @Value("${distrib.packaging.allowedurls:}")
    String allowedUrls;
	
    @Value("${distrib.packaging.allowedRedirects:1}")
    int allowedRedirects;
    
    @Autowired LongTermStorage          lts;    // set via getter below
    @Autowired MimetypesFileTypeMap mimemap;    // set via getter below

    /**
     * the storage service to use to access the bags
     */
    @Bean
    public LongTermStorage getLongTermStorage() throws ConfigurationException {
        try {
            if (mode.equals("aws") || mode.equals("remote")) 
                return new AWSS3LongTermStorage(bagstore, getAmazonS3());
            else if (mode.equals("local")) 
                return new FilesystemLongTermStorage(bagstore);
            else
                throw new ConfigurationException("distrib.bagstore.mode",
                                                 "Unsupported storage mode: "+ mode);
        }
        catch (FileNotFoundException ex) {
            throw new ConfigurationException("distrib.bagstore.location",
                                             "Storage Location not found: "+ex.getMessage(), ex);
        }
    }

    /**
     * the client for access S3 storage
     */
    public AmazonS3 getAmazonS3() throws ConfigurationException {
        logger.info("Creating S3 client");

        if (mode.equals("remote"))
            throw new ConfigurationException("Remote credentials not supported yet");

        // import credentials from the EC2 machine we are running on
        InstanceProfileCredentialsProvider provider = InstanceProfileCredentialsProvider.getInstance();

        AmazonS3 client = AmazonS3Client.builder()
                                        .standard()                 
                                        .withCredentials(provider)
                                        .withRegion(region)
                                        .build();
        return client;
    }

    /**
     * the MIME type assignments to use when setting content types
     */
    @Bean
    public MimetypesFileTypeMap getMimetypesFileTypeMap() {
        InputStream mis = getClass().getResourceAsStream("/mime.types");
        if (mis == null) {
            logger.warn("No mime.type resource found; content type support will be limited!");
            return new MimetypesFileTypeMap();
        }
        return new MimetypesFileTypeMap(mis);
    }

    /**
     * the service implementation to use to download data products.
     */
    @Bean
    public FileDownloadService getFileDownloadService() {
        return new NerdmDrivenFromBagFileDownloadService(lts, mimemap);
    }
        
    /**
     * the service implementation to use to download data products.
     */
    @Bean
    public PreservationBagService getPreservationBagService() {
        return new DefaultPreservationBagService(lts, mimemap);
    }

    /**
     * the service implementation to use to package data into bundles
     */
    @Bean
    public DataPackagingService getDataPackagingService(){
        return new DefaultDataPackagingService(allowedUrls, maxPkgSize, maxFileCount, allowedRedirects);
    }

    /**
     * configure MVC model, including setting CORS support and semicolon in URLs.
     * <p>
     * This gets called as a result of having the @SpringBootApplication annotation.
     * <p>
     * The returned configurer allows requested files to have semicolons in them.  By 
     * default, spring will truncate URLs after the location of a semicolon.  
     */
    @Bean
    public WebMvcConfigurer mvcConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**");
            }

            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                UrlPathHelper uhlpr = configurer.getUrlPathHelper();
                if (uhlpr == null) {
                    uhlpr = new UrlPathHelper();
                    configurer.setUrlPathHelper(uhlpr);
                }
                uhlpr.setRemoveSemicolonContent(false);
            }
        };
    }

    /**
     * the spring-boot application main()
     */
    public static void main(String[] args) {
        // run with the configuration set here
        SpringApplication.run( NISTDistribServiceConfig.class, args );
    }
}

