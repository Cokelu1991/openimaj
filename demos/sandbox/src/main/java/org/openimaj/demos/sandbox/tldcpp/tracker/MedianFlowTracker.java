package org.openimaj.demos.sandbox.tldcpp.tracker;

import org.openimaj.image.FImage;
import org.openimaj.image.analysis.algorithm.TemplateMatcher.TemplateMatcherMode;
import org.openimaj.math.geometry.line.Line2d;
import org.openimaj.math.geometry.point.Point2dImpl;
import org.openimaj.math.geometry.shape.Rectangle;
import org.openimaj.video.tracking.klt.Feature;
import org.openimaj.video.tracking.klt.FeatureList;
import org.openimaj.video.tracking.klt.KLTTracker;
import org.openimaj.video.tracking.klt.TrackingContext;

public class MedianFlowTracker {
	public Rectangle trackerBB;
	private TrackingContext context;
	private KLTTracker klttracker;
	public FeatureList featuresTrackedFrom;
	public FeatureList featuresTrackedTo;

	public MedianFlowTracker() {
		trackerBB = null;
		this.context = new TrackingContext();
		klttracker = new KLTTracker(context, null);
	}

	public void cleanPreviousData() {
		trackerBB = null;
	}

	public void track(FImage prevMat, FImage currMat, Rectangle prevBB) {
		if (prevBB != null) {
			if (prevBB.width <= 0 || prevBB.height <= 0) {
				return;
			}
			float bb_tracker[] = { prevBB.x, prevBB.y, prevBB.width + prevBB.x - 1, prevBB.height + prevBB.y - 1 };

			FImage prevImg = prevMat;
			FImage currImg = currMat;

			boolean success = fbtrack(prevImg, currImg, bb_tracker, bb_tracker);

			// Extract subimage
			float x, y, w, h;
			x = (float) Math.floor(bb_tracker[0] + 0.5);
			y = (float) Math.floor(bb_tracker[1] + 0.5);
			w = (float) Math.floor(bb_tracker[2] - bb_tracker[0] + 1 + 0.5);
			h = (float) Math.floor(bb_tracker[3] - bb_tracker[1] + 1 + 0.5);

			// TODO: Introduce a check for a minimum size
			if (!success 
					|| x < 0 || y < 0 || w <= 0 || h <= 0
					|| x + w > currMat.width || y + h > currMat.height
					|| x != x || y != y || w != w || h != h) { // x!=x is check
																// for nan
				// Leave it empty
			} else {
				
				trackerBB = new Rectangle(x, y, w, h);
			}
		}
	}

	private boolean fbtrack(FImage imgI, FImage imgJ, float[] bb, float[] bbnew) {
		int numM = 10;
		int numN = 10;
		int nPoints = numM * numN;
		int sizePointsArray = nPoints;

		boolean[] status = new boolean[nPoints];

		// float [] pt = new float[sizePointsArray];
		// float [] ptTracked = new float[sizePointsArray];

		FeatureList pt = new FeatureList(sizePointsArray);

		int i;
		int nRealPoints;
		float medFb;
		float medNcc;
		int nAfterFbUsage;
		getFilledBBPoints(bb, numM, numN, 5, pt);
		// getFilledBBPoints(bb, numM, numN, 5, &ptTracked);

		// initImgs();
		FeatureList ptTracked = trackLK(imgI, imgJ, pt, status); // the points
																	// tracked
																	// to the
																	// next
																	// image,
																	// with the
																	// ncc 
																	// (from start -> next) and
																	// fb 
																	// (from next -> start)
		// initImgs();
		// char* status = *statusP;
		int nlkPoints = 0;
		for (i = 0; i < nPoints; i++) {
			nlkPoints += status[i] ? 1 : 0;
		}
		// startPoints = (CvPoint2D32f*) malloc(nlkPoints *
		// sizeof(CvPoint2D32f));
		// targetPoints = (CvPoint2D32f*) malloc(nlkPoints *
		// sizeof(CvPoint2D32f));
		// fbLkCleaned = (float*) malloc(nlkPoints * sizeof(float));
		// nccLkCleaned = (float*) malloc(nlkPoints * sizeof(float));
		FeatureList cleanedTracked = new FeatureList(nlkPoints);
		FeatureList cleanedStart = new FeatureList(nlkPoints);
		nRealPoints = 0;
		float[] fbLkCleaned = new float[nlkPoints];
		float[] nccLkCleaned = new float[nlkPoints];
		for (i = 0; i < nPoints; i++) {
			// TODO:handle Missing Points
			// or status[i]==0
			if (ptTracked.features[i] == null || ptTracked.features[i].val < 0 || !status[i]) {
			} else {
				cleanedStart.features[nRealPoints] = pt.features[i].clone();
				cleanedTracked.features[nRealPoints] = ptTracked.features[i].clone();
				fbLkCleaned[nRealPoints] = ((FBNCCFeature) cleanedTracked.features[nRealPoints]).fbDistance;
				nccLkCleaned[nRealPoints] = ((FBNCCFeature) cleanedTracked.features[nRealPoints]).ncc;
				nRealPoints++;
			}
		}
		if(nlkPoints == 0) return false;
		// assert nRealPoints==nlkPoints
		medFb = FastMedian.getMedian(fbLkCleaned, nlkPoints);
		medNcc = FastMedian.getMedian(nccLkCleaned, nlkPoints);
		/*
		 * printf("medianfb: %f\nmedianncc: %f\n", medFb, medNcc);
		 * printf("Number of points after lk: %d\n", nlkPoints);
		 */
		nAfterFbUsage = 0;
		for (i = 0; i < nlkPoints; i++) {
			if ((fbLkCleaned[i] <= medFb) & (nccLkCleaned[i] >= medNcc)) {
				cleanedStart.features[nAfterFbUsage] = cleanedStart.features[i];
				cleanedTracked.features[nAfterFbUsage] = cleanedTracked.features[i];
				nAfterFbUsage++;
			}
		}
		/* printf("Number of points after fb correction: %d\n", nAfterFbUsage); */
		// showIplImage(IMGS[1]);
		// show "OpticalFlow" fb filtered.
		// drawLinesCvPoint2D32f(imgI, startPoints, nRealPoints, targetPoints,
		// nRealPoints);
		// showIplImage(imgI);
		predictbb(bb, cleanedStart, cleanedTracked, nAfterFbUsage, bbnew);
		this.featuresTrackedFrom = new FeatureList(nAfterFbUsage);
		System.arraycopy(cleanedStart.features, 0, this.featuresTrackedFrom.features, 0, nAfterFbUsage);
		this.featuresTrackedTo = new FeatureList(nAfterFbUsage);
		System.arraycopy(cleanedTracked.features, 0, this.featuresTrackedTo.features, 0, nAfterFbUsage);
		/*
		 * printf("bbnew: %f,%f,%f,%f\n", bbnew[0], bbnew[1], bbnew[2],
		 * bbnew[3]); printf("relative scale: %f \n", scaleshift[0]);
		 */

		if (medFb > 10)
			return false;
		else
			return true;
		
	}

	private boolean predictbb(float[] bb0, FeatureList pt0, FeatureList pt1, int nPts, float []bb1) {
		float[] ofx = new float[nPts];
		float[] ofy = new float[nPts];
		int i;
		int j;
		int d = 0;
		float dx,dy;
		int lenPdist;
		float[] dist0;
		float[] dist1;
		float s0,s1;
		for (i = 0; i < nPts; i++)
		{
			ofx[i] = pt1.features[i].x - pt0.features[i].x;
			ofy[i] = pt1.features[i].y - pt0.features[i].y;
		}
		dx = FastMedian.getMedianUnmanaged(ofx, nPts);
		dy = FastMedian.getMedianUnmanaged(ofy, nPts);
		ofx = null;
		ofy = null;
		//m(m-1)/2
		lenPdist = nPts * (nPts - 1) / 2;
		dist0 = new float[lenPdist];
		dist1 = new float[lenPdist];
		for (i = 0; i < nPts; i++)
		{
			for (j = i + 1; j < nPts; j++, d++)
			{
				dist0[d] = (float) Math.sqrt(
					Math.pow(pt0.features[i].x - pt0.features[j].x, 2) + 
					Math.pow(pt0.features[i].y - pt0.features[j].y, 2)
				);
				dist1[d] = (float) Math.sqrt(Math.pow(pt1.features[i].x - pt1.features[j].x, 2) + Math.pow(pt1.features[i].y - pt1.features[j].y, 2));
				dist0[d] = dist1[d] / dist0[d];
			}
		}
		// The scale change is the median of all changes of distance.
		// same as s = median(d2./d1) with above
		float shift = 0f;
		if(lenPdist == 0){
			
		}
		else{
			shift = FastMedian.getMedianUnmanaged(dist0, lenPdist);			
		}
//		float shift = 1;
		dist0 = null;
		dist1 = null;
		s0 = 0.5f * (shift - 1) * getBbWidth(bb0);
		s1 = 0.5f * (shift - 1) * getBbHeight(bb0);

		// Apply transformations (translation& scale) to old Bounding Box
		bb1[0] = bb0[0] - s0 + dx;
		bb1[1] = bb0[1] - s1 + dy;
		bb1[2] = bb0[2] + s0 + dx;
		bb1[3] = bb0[3] + s1 + dy;

		//return absolute scale change
		//  shift[0] = s0;
		//  shift[1] = s1;
		return true;
	}

	private float getBbHeight(float[] bb) {
		return Math.abs(bb[3] - bb[1] + 1);
	}

	private float getBbWidth(float[] bb) {
		return Math.abs(bb[2] - bb[0] + 1);
	}

	private FeatureList trackLK(FImage imgI, FImage imgJ, FeatureList pt,
			boolean[] status) {

		// TODO: watch NaN cases
		// double nan = std::numeric_limits<double>::quiet_NaN();
		// double inf = std::numeric_limits<double>::infinity();

		// tracking
		int I, J, winsize_ncc;
		int i;
		I = 0;
		J = 1;
		winsize_ncc = 10;

		// NOTE: initImgs() must be used correctly or memleak will follow.

		// lucas kanade track
		// cvCalcOpticalFlowPyrLK(imgI, imgJ, PYR[I], PYR[J], points[0],
		// points[1],
		// nPtsI, cvSize(win_size_lk, win_size_lk), level, status, 0,
		// cvTermCriteria(CV_TERMCRIT_ITER | CV_TERMCRIT_EPS, 20, 0.03),
		// CV_LKFLOW_INITIAL_GUESSES);
		//
		// //backtrack
		// cvCalcOpticalFlowPyrLK(imgJ, imgI, PYR[J], PYR[I], points[1],
		// points[2],
		// nPtsI, cvSize(win_size_lk, win_size_lk), level, statusBacktrack, 0,
		// cvTermCriteria(
		// CV_TERMCRIT_ITER | CV_TERMCRIT_EPS, 20, 0.03),
		// CV_LKFLOW_INITIAL_GUESSES | CV_LKFLOW_PYR_A_READY |
		// CV_LKFLOW_PYR_B_READY);

		// Set the starting points (the grid)
		this.klttracker.setFeatureList(pt);
		// Track the grid to the next image
		this.klttracker.trackFeatures(imgI, imgJ);
		// Store the tracked points (these will be the output also)
		FeatureList ptTracked = this.klttracker.getFeatureList().clone();
		// Track these points back to the first image
		this.klttracker.trackFeatures(imgJ, imgI);
		// Hold the tracked back points
		FeatureList trackedBack = this.klttracker.getFeatureList();
		// Figure out which points failed
		for (i = 0; i < ptTracked.features.length; i++) {
			if (trackedBack.features[i].val >= 0
					&& ptTracked.features[i].val >= 0) {
				status[i] = true;
			} else {
				status[i] = false;
			}
		}
		// set the ncc in the ptTracked points
		normCrossCorrelation(imgI, imgJ, pt, ptTracked, status, winsize_ncc);
		// set the fbDistance in the ptTracked points
		euclideanDistance(pt, trackedBack, ptTracked, status); // compare the
																// first two and
																// store the
																// results in
																// the last one

		return ptTracked;
	}

	private void euclideanDistance(FeatureList pt, FeatureList trackedBack,
			FeatureList ptTracked, boolean[] status) {
		for (int i = 0; i < status.length; i++) {
			boolean tracked = status[i];
			FBNCCFeature feat = (FBNCCFeature) pt.features[i];
			FBNCCFeature trackedBackFeat = (FBNCCFeature) trackedBack.features[i];
			FBNCCFeature storageFeat = (FBNCCFeature) ptTracked.features[i];
			if (tracked) {
				feat.fbDistance = (float) Line2d
						.distance(feat, trackedBackFeat);
			} else {
				feat.fbDistance = Float.NaN;
			}
		}
	}

	private void normCrossCorrelation(FImage imgI, FImage imgJ, FeatureList pt,
			FeatureList ptTracked, boolean[] status, int winsize_ncc) {
		for (int i = 0; i < status.length; i++) {
			boolean tracked = status[i];
			FBNCCFeature feat = (FBNCCFeature) pt.features[i];
			FBNCCFeature featTracked = (FBNCCFeature) ptTracked.features[i];
			if (tracked) {
				feat.ncc = TemplateMatcherMode.NORM_SUM_SQUARED_DIFFERENCE
						.computeMatchScore(imgI.pixels, (int) feat.x,
								(int) feat.y, imgJ.pixels, (int) featTracked.x,
								(int) featTracked.y, winsize_ncc, winsize_ncc);
			} else {
				feat.ncc = Float.NaN;
			}
		}
	}

	/**
	 * Creates numM x numN points grid on BBox. Points ordered in 1 dimensional
	 * array (x1, y1, x2, y2).
	 * 
	 * @param bb
	 *            Bounding box represented through 2 points(x1,y1,x2,y2)
	 * @param numM
	 *            Number of points in height direction.
	 * @param numN
	 *            Number of points in width direction.
	 * @param margin
	 *            margin (in pixel)
	 * @param pt2
	 *            Contains the calculated points in the form (x1, y1, x2, y2).
	 *            Size of the array must be numM * numN * 2.
	 */
	boolean getFilledBBPoints(float[] bb, int numM, int numN, int margin,
			FeatureList pt2) {
		FeatureList pt = pt2;
		int pointDim = 2;
		int i;
		int j;
		float[] pbb_local;
		/**
		 * gap between points in width direction
		 */
		float divN = 0;
		/**
		 * gap between points in height direction
		 */
		float divM = 0;
		float[] bb_local = new float[4];
		float spaceN;
		float spaceM;
		Feature cen;
		/* add margin */
		bb_local[0] = bb[0] + margin;
		bb_local[1] = bb[1] + margin;
		bb_local[2] = bb[2] - margin;
		bb_local[3] = bb[3] - margin;
		pbb_local = bb_local;
		/* printf("PointArraySize should be: %d\n", numM * numN * pointDim); */
		/* handle cases numX = 1 */// If there is 1 point pick one in the middle
		if (numN == 1 && numM == 1) {
			Feature center = calculateBBCenter(pbb_local);
			center.val = KLTTracker.KLT_TRACKED;
			pt2.features[0] = center;
			return true;
		} else if (numN == 1 && numM > 1) {
			divM = numM - 1;
			divN = 2;
			/* maybe save center coordinate into bb[1] instead of loop again */
			/* calculate step width */
			spaceM = (bb_local[3] - bb_local[1]) / divM;
			cen = calculateBBCenter(pbb_local);
			/* calculate points and save them to the array */
			for (i = 0; i < numN; i++) {
				for (j = 0; j < numM; j++) {
					pt.features[i * numM + j] = new FBNCCFeature();
					Feature f = pt.features[i * numM + j];
					f.x = cen.x;
					f.y = bb_local[1] + j * spaceM;
					f.val = KLTTracker.KLT_TRACKED;
				}
			}
			return true;
		} else if (numN > 1 && numM == 1) {
			divM = 2;
			divN = numN - 1;
			// maybe save center coordinate into bb[1] instead of loop again
			// calculate step width
			spaceN = (bb_local[2] - bb_local[0]) / divN;
			cen = calculateBBCenter(pbb_local);
			// calculate points and save them to the array
			for (i = 0; i < numN; i++) {
				for (j = 0; j < numM; j++) {
					pt.features[i * numM + j] = new FBNCCFeature();
					Feature f = pt.features[i * numM + j];
					f.x = bb_local[0] + i * spaceN;
					f.y = cen.y;
					f.val = KLTTracker.KLT_TRACKED;
				}
			}
			return true;
		} else if (numN > 1 && numM > 1) {
			divM = numM - 1;
			divN = numN - 1;
		}
		// calculate step width
		spaceN = (bb_local[2] - bb_local[0]) / divN;
		spaceM = (bb_local[3] - bb_local[1]) / divM;
		// calculate points and save them to the array
		for (i = 0; i < numN; i++) {
			for (j = 0; j < numM; j++) {
				pt.features[i * numM + j] = new FBNCCFeature();
				Feature f = pt.features[i * numM + j];
				f.x = bb_local[0] + i * spaceN;
				f.y = bb_local[1] + j * spaceM;
				f.val = KLTTracker.KLT_TRACKED;
			}
		}
		return true;
	}

	/**
	 * Calculates center of a Rectangle/Boundingbox.
	 * 
	 * @param bb
	 *            defined with 2 points x,y,x1,y1
	 */
	Feature calculateBBCenter(float[] bb) {
		float[] bbnow = bb;
		Feature centernow = null;
		if (bbnow == null) {
			return null;
		}
		centernow = new FBNCCFeature();
		centernow.x = 0.5f * (bbnow[0] + bbnow[2]);
		centernow.y = 0.5f * (bbnow[1] + bbnow[3]);
		return centernow;
	}
}
