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
 * @author: Deoyani Nandrekar-Heinis
 */
package gov.nist.oar.distrib.service;

import java.io.OutputStream;
import java.util.zip.ZipOutputStream;

import gov.nist.oar.distrib.DataPackager;
import gov.nist.oar.distrib.DistributionException;
import gov.nist.oar.distrib.web.FilePathUrl;

/**
 * @author Deoyani Nandrekar-Heinis
 *
 */
public interface DataPackagingService {
  public OutputStream getPackageFor(FilePathUrl[] jsonRequest, String format)throws DistributionException ;
  public void getZipPackage(ZipOutputStream zout)throws DistributionException ;
}
