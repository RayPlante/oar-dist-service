package gov.nist.mml.oar.ds.service.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import gov.nist.mml.oar.ds.s3.S3Wrapper;
import gov.nist.mml.oar.ds.service.CacheManager;
import gov.nist.mml.oar.ds.service.DownloadService;
import gov.nist.mml.oar.ds.service.MappingUtils;

@Service
public class DownloadServiceImpl implements DownloadService{
	

	@Autowired
	private S3Wrapper s3Wrapper;
	
	@Value("${cloud.aws.preservation.s3.bucket}")
	private String preservationBucket; 
	
	@Value("${cloud.aws.cache.s3.bucket}")
	private String cacheBucket; 
	
	@Autowired
	private CacheManager cacheManager;
	
	
	private static final String ORE_FILE_PATTERN = "ore.jsonld";
	private static final String BAG_FILE_ID = ".bag.";

 
	public List<PutObjectResult> uploadToCache(MultipartFile[] multipartFiles) {
		return s3Wrapper.upload(cacheBucket, multipartFiles);
	}

 
	@Override
	public ResponseEntity<byte[]> downloadDistFile(String dsId, String distId) throws IOException {
 			String fileKey = getDistFileKey(dsId,distId);
			if(fileKey != null){
				return s3Wrapper.download(cacheBucket, fileKey);
 			}
			return null;
	}
	
	private String getDistFileKey(String dsId, String distId) throws IOException {
		String mappingFile = getMappingFile(dsId);
		if(!StringUtils.isEmpty(mappingFile)){
			return  MappingUtils.findDistFileKey(distId, mappingFile);
		}
		return null;
	}
	
	
	@Override
	public ResponseEntity<List<String>> findDataSetBags(String dsId) throws IOException {
 			List<S3ObjectSummary> bagSummaries = s3Wrapper.list(cacheBucket, dsId+BAG_FILE_ID);
			Collections.sort(bagSummaries, (bag1, bag2) -> bag2.getKey().compareTo(bag1.getKey()));
 			List<String> results = new ArrayList<String>();
 			for(S3ObjectSummary sum: bagSummaries){
 				results.add(sum.getKey());
 			}
 			return new ResponseEntity<>(results, HttpStatus.OK);
	}
	
	
	private  String  getMappingFile(String dsId) throws IOException {
		ResponseEntity<byte[]> mappingFile = s3Wrapper.download(cacheBucket, dsId+"."+ ORE_FILE_PATTERN);
		byte[] result = mappingFile.getBody();
		return IOUtils.toString(result, "UTF-8");
	}
	

	
}
