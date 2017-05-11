/*******************************************************************************
 * Copyright (c) 2017 Actuate. All Rights Reserved.
 * Trademarks owned by Actuate.
 * "OpenText" is a trademark of Open Text.
 *******************************************************************************/

package com.actuate.birt.script.ext.rserve;

import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPMismatchException;

/**
 * Utility class to perform REXP vector type conversions. This class works around some bugs in REXP
 * classes implementation, and offers consistent handling of NA values in R vector:
 *    - NA value is always returned as REXPInteger.NA or REXPDouble.NA if return type is primitive array
 *    - NA value is always returned as null for object arrays
 */
public class RVectorConverter {

	public static double[] to_doubles( REXP source ) throws REXPMismatchException {
		if ( source.isInteger() ) {
			// Make sure the integer NA is converted to double NA, instead of Integer.MIN_VALUE
			int[] ints = source.asIntegers();
			double[] ret = new double[ints.length];
			for ( int i = 0; i < ints.length; i++) {
				if (ints[i] == REXPInteger.NA)
					ret[i] = REXPDouble.NA;
				else 
					ret[i] = ints[i];
			}
			return ret;
		} else {
			return source.asDoubles();
		}
	}
	
	public static Double[] to_Doubles( REXP source ) throws REXPMismatchException {
		if ( source.isInteger() ) {
			// Make sure the integer NA is converted to null, instead of Integer.MIN_VALUE
			int[] ints = source.asIntegers();
			Double[] ret = new Double[ints.length];
			for ( int i = 0; i < ints.length; i++) {
				if (ints[i] == REXPInteger.NA)
					ret[i] = null;
				else 
					ret[i] = (double)ints[i];
			}
			return ret;
		} else {
			double[] d = source.asDoubles();
			Double[] ret = new Double[d.length];
			for ( int i = 0; i < d.length; i++) {
				// NA is converted to null
				if ( REXPDouble.isNA( d[i] ))
					ret[i] = null;
				else
					ret[i] = d[i];
			}
			return ret;
		}
	}
	
	public static int[] to_ints( REXP source ) throws REXPMismatchException {
		if ( source instanceof REXPDouble ) {
			double[] d = source.asDoubles();
			// For consistency, convert double NA to integer NA, 
			// rather than 0 (as happens when Double.NaN is cast to int)
			int[] ret = new int[d.length];
			for ( int i = 0; i < d.length; i++) {
				if ( REXPDouble.isNA( d[i] ))
					ret[i] = REXPInteger.NA;
				else 
					ret[i] = (int)d[i];
			}
			return ret;
		} else {
			return source.asIntegers();
		}
	}
	
	public static Integer[] to_Integers( REXP source ) throws REXPMismatchException {
		if ( source instanceof REXPDouble ) {
			double[] d = source.asDoubles();
			// Convert NA to null
			Integer[] ret = new Integer[d.length];
			for ( int i = 0; i < d.length; i++) {
				if ( REXPDouble.isNA( d[i] ))
					ret[i] = null;
				else 
					ret[i] = (int)d[i];
			}
			return ret;
		} else {
			int[] ints = source.asIntegers();
			Integer[] ret = new Integer[ints.length];
			for ( int i = 0; i < ints.length; i++) {
				// NA is converted to null
				if ( ints[i] == REXPInteger.NA )
					ret[i] = null;
				else
					ret[i] = ints[i];
			}
			return ret;
		}
	}
	
	public static String[] to_Strings( REXP source ) throws REXPMismatchException {
		String[] ret = source.asStrings();
		
		// change all NA values to null
		if ( source instanceof REXPInteger ) {
			int[] payload = source.asIntegers();
			for (int i = 0; i < payload.length; i++ ) {
				if ( payload[i] == REXPInteger.NA )
					ret[i] = null;
			}
		} else if (source instanceof REXPDouble ) {
			double[] payload = source.asDoubles();
			for (int i = 0; i < payload.length; i++ ) {
				if ( REXPDouble.isNA( payload[i] ) )
					ret[i] = null;
			}
		}
		
		return ret;
	}
	
	public static double[][] to_doubleMatrix( REXP source ) throws REXPMismatchException {
		double[][] ret = source.asDoubleMatrix();
		
		int rows = ret.length;
		int cols = rows > 0? ret[0].length : 0;
		
		// Fix int->double conversion for NA values
		if ( source.isInteger() ) {
			for ( int r = 0; r < rows; r++ ) 
				for ( int c = 0; c < cols; c++ ) {
					if ( ret[r][c] == REXPInteger.NA )
						ret[r][c] = REXPDouble.NA;
				}
		}
		
		return ret;
	}
}
