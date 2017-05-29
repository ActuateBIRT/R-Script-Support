/*******************************************************************************
 * Copyright (c) 2017 Actuate. All Rights Reserved.
 * Trademarks owned by Actuate.
 * "OpenText" is a trademark of Open Text.
 *******************************************************************************/
package com.actuate.birt.script.ext.rserve;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPDouble;
import org.rosuda.REngine.REXPInteger;
import org.rosuda.REngine.REXPList;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REXPNull;
import org.rosuda.REngine.REXPString;
import org.rosuda.REngine.REXPVector;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import com.actuate.birt.script.ext.rserve.RserveConnectionFactory.RConnectionWrapper;


/**
 * Implements a Rserve-based script engine that runs R scripts
 */
public class RScriptEngine extends AbstractScriptEngine {
	
	// Predefined attributes required to evaluate a script

	/** RConnection to use to run R script */
	public static String ATTR_R_CONNECTION = "#r.connection";

	/** Class of evaluation result */
	public static String BINDING_RESULT_CLASS = "#result.class";
	
	private static final String DEFAULT_COLUMN_NAME = "column_";
	private static final String COLUMN_ROW_NAME = "row_name";
	
	private static Logger logger = Logger.getLogger( RScriptEngine.class.getName() );
	
	protected RScriptEngineFactory factory;
	
	public RScriptEngine(RScriptEngineFactory factory ) {
		this.factory = factory;
	}
	
	@Override
	public Object eval(String script, ScriptContext context) throws ScriptException {
		// Get RConnection to use for evaluation; this is required
		RConnection conn = getRConnection(context);
		if ( conn == null )
			throw new ScriptException("Failed to get R connection");
		
		script = fixScriptLineBreak(script);
		
		// Get desired eval result class; this is optional
		Class<?> resultClass = (Class<?>)context.getAttribute(BINDING_RESULT_CLASS);
		try {
			if ( resultClass == null ) {
				// Determine best return type based on R result
				return evalAutoType( script, conn);
			} else if ( resultClass == void.class ) {
				// No return result expected
				conn.voidEval( script );
				return null;
			} else {
				return evalAsType(script, resultClass, conn);
			}
		} catch (RserveException rse)  {
			throw handleRserveException(conn, rse);
		} catch ( REXPMismatchException e ) {
			throw new ScriptException(e);
		}
	}
	
	/**
	 * Create a ScriptException that best describe the root cause of an RserveException
	 */
	private ScriptException handleRserveException(RConnection conn, RserveException rse) {
		String errMsg = null;
		
		// For generic error 127, we can follow up with a R function call geterrmessage()
		// to find out root cause
		if ( conn != null && rse.getRequestReturnCode() == 127 ) {
			try {
				REXP errExp = conn.eval( "geterrmessage()");
				errMsg = errExp.asString();
			} catch ( RserveException | REXPMismatchException e ) {
				// Ignore error; fall through to just report generic error message
			}
		}

		if ( errMsg == null || errMsg.isEmpty()) {
			// Other types of error (such as parser error), or if we were not able to run geterrmessage()
			errMsg = rse.getLocalizedMessage();
		}
		ScriptException se = new ScriptException( errMsg );
		se.initCause( rse );
		return se;
	}

	/**
	 * Gets the RConnection associated with script context
	 */
	private RConnection getRConnection(ScriptContext context) {
		Object connObj = context.getAttribute( ATTR_R_CONNECTION );
		if ( connObj == null )
			throw new RuntimeException("Missing required attribute: " + ATTR_R_CONNECTION);
		
		// The connection can be a wrapper returned by our connection factory
		RConnection conn;
		if ( connObj instanceof RConnectionWrapper ) {
			conn = ( (RConnectionWrapper) connObj).getRConnection();
		} else {
			conn = (RConnection) connObj;
		}
		return conn;
	}
	
	/**
	 * Replace DOS line breaks in script text; RConnection doesn't appear to like those
	 */
	private String fixScriptLineBreak(String script) {
		return script.replace("\r\n", "\n");
	}

	
	@Override
	public Object eval(Reader reader, ScriptContext context) throws ScriptException {
		// Read all script text to string
		StringBuilder sb = new StringBuilder();
		char[] cbuf = new char[4000];
		int len = 0;
		try {
			while ( (len = reader.read(cbuf)) > 0) {
				sb.append(cbuf, 0, len);
			}
		} catch (IOException e) {
			logger.log( Level.WARNING, "Failed to read script", e);
			throw new ScriptException( e );
		}
		
		return eval( sb.toString(), context);
	}

	/**
	 * Creates a SimpleBindings instance
	 */
	@Override
	public Bindings createBindings() {
		return new SimpleBindings();
	}

	@Override
	public ScriptEngineFactory getFactory() {
		return factory;
	}

	/**
	 * Sets an attribute value. If attribute name starts with '#' it is handled locally;
	 * otherwise, key/value pair are sent to Rserve 
	 */
	@Override
	public void put(String key, Object value) {
		if ( key.isEmpty() || key.isEmpty() )
			throw new IllegalArgumentException("key is empty");
		
		if (key.charAt(0) == '#') {
			// Put key/value in local binding
			super.put(key, value);
		} else {
			RConnection rconn = getRConnection( this.getContext() );
			assignRVariable(key, value, rconn);
		}
	}

	/**
	 * Evaluate script and cast result to specified type.
	 * Note on NA values: NA is generally returned as null. However, if the requested type is a primitive
	 * array (int[] or double[]), an element that is NA will be returned as either Integer.MIN_VALUE (for int[])
	 * or Double.NaN.
	 * @param script R statements to evaluate
	 * @param type Requested output type. Supported types are: int, int[], Integer[],
	 *        double, double[], Double[], String, String[], double[][], byte[], Map (for data frames) 
	 * @param rconn RConnection to use for evaluation
	 */
	public Object evalAsType(String script, Class<?> type, RConnection rconn) 
			throws RserveException, REXPMismatchException, ScriptException {
		REXP result = rconn.eval( script );
		if (result.isNull())
			return null;
		
		if ( type == Integer.class || type == int.class) {
			return RVectorConverter.to_Integers( result )[0];
		} else if ( Number.class.isAssignableFrom( type ) || type == double.class ) {
			return RVectorConverter.to_Doubles( result )[0];
		} else if ( type == double[].class ) {
			return RVectorConverter.to_doubles( result );
		} else if ( type == int[].class ) {
			return RVectorConverter.to_ints( result );
		} else if ( type == Double[].class) {
			return RVectorConverter.to_Doubles( result );
		} else if ( type == Integer[].class ) {
			return RVectorConverter.to_Integers( result );
		}
		else if ( type == String.class) {
			return RVectorConverter.to_Strings( result )[0];
		} else if ( type == String[].class) {
			return RVectorConverter.to_Strings( result );
		} else if ( type == double[][].class ) {
			return RVectorConverter.to_doubleMatrix( result );
		} else if ( type == byte[].class) {
			return result.asBytes();
		} else if ( Map.class.isAssignableFrom(type)) {
			return mapFromREXP(result);
		} else {
			throw new ScriptException( "Invalid type: " + type.getName() );
		}
	}

	/**
	 * Converts an rexp to a [name (String) -> value (Array)] map
	 * 
	 * @param rexp
	 * @return
	 * @throws REXPMismatchException
	 */
	private Map<String, Object> mapFromREXP( REXP rexp )
			throws REXPMismatchException {
		if ( rexp.isList( ) ) {
			return mapFromDataFrame( rexp );
		}
		// cover matrix and array:
		LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>( );
		int[] dimension = rexp.dim( );
		int arraylength = rexp.length( );

		if ( ( dimension != null ) && ( dimension.length == 2 )
				&& rexp.isNumeric( ) ) { // matrix
			double[] vectorOfDoubles =  RVectorConverter.to_doubles( rexp );
			int numOfRows = dimension[0];
			for ( int col = 0; col < dimension[1]; col++ ) {
				double[] singleColumn = Arrays.copyOfRange( vectorOfDoubles,
						col * numOfRows, numOfRows * ( col + 1 ) );
				result.put( DEFAULT_COLUMN_NAME + ( col + 1 ), singleColumn );
			}
		}
		else if ( ( dimension != null ) && ( dimension.length > 2 ) ) {
			throw new REXPMismatchException( rexp,
					"more than 2 dimension is not supported" );
		}
		else if ( arraylength > 0 ) {
			result.put( DEFAULT_COLUMN_NAME + "1", rexp.asNativeJavaObject( ) );
		}
		else {
			throw new REXPMismatchException( rexp,
					"output is not supported" );
		}

		return result;
	}
	
	/**
	 * Converts a data frame to a [name (String) -> value (Array)] map 
	 * The map's key (i.e. column name) list preserves the original order of keys 
	 */
	private Map<String, Object> mapFromDataFrame( REXP rexp ) throws REXPMismatchException {
		// Use linkedhashmap to preserve key order (i.e., column name order)
		LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
		
		// First column: row names (if present)
		REXP reNames = rexp.getAttribute("row.names");
		if (reNames != null && ! reNames.isInteger( ) ) {
			Object rNames = reNames.asNativeJavaObject();
			result.put(COLUMN_ROW_NAME, rNames);
		}
		else if ( reNames != null && reNames.isInteger( ) ) { //custom index
			int[] numericIndex = reNames.asIntegers( );
			if ( numericIndex.length != 2
					|| numericIndex[0] != Integer.MIN_VALUE ) {
				result.put( COLUMN_ROW_NAME, numericIndex );
			} else {
				// NA followed by a negative number L means an auto index of [1 ... -L]
				int len = - numericIndex[1];
				if ( len > 0 ) {
					int[] autoIndex = new int[len];
					for ( int i = 0; i < len; i++ )
						autoIndex[i] = i + 1;
					result.put( COLUMN_ROW_NAME, autoIndex );
				}
			}
		}

		// Data from RList is retrieved by columns; each column is a vector
		RList rlist = rexp.asList();
		int nCols = rlist.size();

		String[] columnNames = rlist.keys();
		for (int col = 0; col < nCols; col++) {
			String colName = columnNames[col];

			// Generate unique column name for unnamed columns
			if (colName == null || colName.isEmpty())
				colName = DEFAULT_COLUMN_NAME + (col + 1);

			REXP val = rlist.at(col);
			result.put( colName, val.asNativeJavaObject());
		}
		
		return result;
	}

	
	/**
	 * Converts a 2-d Number array or list to a REXP that represents a R double matrix
	 * @throws BirtException 
	 */
	private REXP toDoubleMatrix(Object[] values)   {
		if ( values instanceof double[][] ) {
			return REXP.createDoubleMatrix( (double[][]) values);
		}
		
		int nrows = values.length;
		if ( nrows == 0 )
			return new REXPNull();

		// Determine 2nd dimension length by looking at first row;
		// First row can be a collection or an array
		Object row0 = values[0];
		if ( row0  == null )
			return new REXPNull();

		int ncols;
		if ( row0 instanceof Collection ) {
			ncols = ((Collection<?>) row0).size();
		} else {
			// row0 must be array
			ncols = Array.getLength( values[0]);
		}
		
		// Copy data to double[][] array
		double[][] matrix = new double[nrows][ncols];
		for ( int r = 0; r < nrows; r++ ) {
			Object row = values[r];
			if ( row instanceof Collection) {
				// Iterate through collection and convert each value to double
				int c = 0;
				for ( Object o : (Collection<?>) row) {
					matrix[r][c] =  toDouble(o);
					if ( (++c) >= ncols )
						break;
				}
			}  else {
				// Row is array
				for ( int c = 0; c < ncols; c++ ) {
					matrix[r][c] = toDouble(Array.get(row,c));
				}
			}
		}
		
		return REXP.createDoubleMatrix( matrix);
	}

	private double toDouble(Object value) {
		if (value == null)
			return REXPDouble.NA;
		if (value instanceof Number)
			return ((Number)value).doubleValue();
		return Double.parseDouble( value.toString() );
	}
	
	/**
	 * Converts a Collection to appropriate REXP 
	 * @throws BirtException 
	 */
	private REXP collectionToREXP(Collection<?> col)  {
		Object[] array = col.toArray();
		return arrayToREXP(array);
	}
	
	/**
	 * Converts an array to appropriate REXP subclass instance
	 * @throws BirtException 
	 */
	private REXP arrayToREXP(Object array)  {
		assert array.getClass().isArray();

		// int, double and String arrays can be directly passed to REXP;
		if ( array instanceof int[] ) {
			return new REXPInteger( (int[]) array);
		} else if (array instanceof double[] ) {
			return new REXPDouble( (double[]) array);
		} else if ( array instanceof String[] ) {
			return new REXPString( (String[]) array );
		}

		// Examine first element to see if it is a collection or array
		Object first = Array.get(array, 0);
		if ( first != null && 
			( first.getClass().isArray() || first instanceof Collection)) {
			// 2-D array or collection; convert to matrix
			return toDoubleMatrix( (Object[]) array);
		}
		
		int len = Array.getLength(array);
		if ( len == 0)
			return new REXPNull();
		
		// Need to unbox or convert data types
		Class<?> baseClass = array.getClass().getComponentType();
		// if array type is Object[], we should find the first non-null value to determine the actual type
		if ( baseClass == Object.class) {
			for (int i = 0; i < len; i++ ) {
				Object elem = Array.get( array, i);
				if ( elem != null ) {
					baseClass = elem.getClass();
					break;
				}
			}
		}
		
		if ( baseClass == Integer.class ) {
			int[] ints = new int[len];
			for (int i = 0; i < len; i++ ) {
				Integer v = (Integer) Array.get(array, i);
				// Convert null to NA
				ints[i] = v == null? REXPInteger.NA : v;
			}
			return  new REXPInteger( ints );
		} else if ( Number.class.isAssignableFrom( baseClass)) {
			//  All other numbers are passed as Double 
			double[] doubles = new double[len];
			for (int i = 0; i < len; i++ ) {
				Number v = ((Number)Array.get(array, i));
				// Convert null to NA
				doubles[i] = v == null? REXPDouble.NA : v.doubleValue();
			}
			return  new REXPDouble( doubles );
		} else {
			// Everything else is converted to string array
			String[] strs = new String[len];
			for (int i = 0; i < len; i++) {
				Object val = Array.get(array, i);
 				strs[i] = val == null? null : val.toString();
			}
			return new REXPString( strs);
		}
	}
	
	/**
	 * Assign a value to an R variable. Value can be one of the following types:
	 *  Number, Number array - assigned to Integer or Double vector, depending on number type
	 *  String, String array - assigned to String vector
	 *  2-d number array - assigned to double frame
	 *  Map - assigned to data frame
	 */
	private void assignRVariable(String var, Object value, RConnection rconn)  {
		try {
			REXP valExp = objectToREXP( value);
			rconn.assign(var, valExp);
		} catch ( ScriptException | REngineException | REXPMismatchException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Converts a java object to REXP. Conversion rules are as follows:
	 *   null -> NULL
	 *   Integer, array of integer, or Collection of integer -> Integer vector
	 *   Number, or array/Collection of numbers -> Double vector
	 *   2-dimensional array/Collection of numbers -> Double matrix
	 *   Map<String, Object> -> A Data Frame, if all mapped values are equal-sized vectors; otherwise a pair list
	 *   String or other types, or array/Collection of such -> String vector
	 */
	@SuppressWarnings("unchecked")
	private REXP objectToREXP( Object value ) throws  ScriptException, REXPMismatchException {
		if (value == null)
			return new REXPNull();
		
		if (value instanceof Map)
			return mapToREXP( (Map<String, Object>) value);
		
		Class<?> cls = value.getClass();
		if ( cls.isArray() ) 
			return arrayToREXP(value);
		
		if ( value instanceof Collection)
			return collectionToREXP ( (Collection<?>) value);
		
		if (value instanceof Integer)
			return new REXPInteger( (Integer) value);
		
		if (value instanceof Number)
			// All other number types are handled as double
			return new REXPDouble( ((Number) value).doubleValue() );
		
		// Treat everything else as a String
		return new REXPString( value.toString() );
	}
	
	/**
	 * Convert a <String -> Object> map to REXP representing either a List or a Data Frame 
	 * A data frame is returned only if all mapped values convert to equal-sized vectors
	 * @throws REXPMismatchException 
	 * @throws BirtException 
	 */ 
	private REXP mapToREXP( Map<String, Object> map ) 
			throws ScriptException, REXPMismatchException{

		int i = 0;
		boolean isDataFrame = true;
		int numRows = -1;
		String[] names = new String[map.size()];
		REXP[] contents = new REXP[map.size()];
				
		// Iterate over <name, value> pairs in map
		for ( Map.Entry<String, Object> entry : map.entrySet() ) {
			names[i] = entry.getKey();
			Object value = entry.getValue();
			REXP valExp = objectToREXP(value);
			contents[i] = valExp;
			++i;
			
			// verify that data frame conditions are still satisfied
			if ( isDataFrame ) {
				if (valExp instanceof REXPVector) {
					// Check vector length
					int len = ((REXPVector) valExp).length();
					if ( numRows < 0  ) {
						numRows = len;	/// first vector; remember length
					} else if ( len != numRows ) {
						// Unequal vector length detected
						isDataFrame = false;
					}
				} else {
					// Non-vector value voids data frame conditions
					isDataFrame = false;
				}
			}
		}
		
		// Construct a named list
		RList rlist = new RList(contents, names);
		if ( isDataFrame )
			return REXP.createDataFrame( rlist );
		else
			return new REXPList( rlist );
	}
	
	/**
	 * Evaluate R script and return Java object that best represents the result based on its R data type
	 * In particular, vectors of length one are de-arrayed and returned as a single value
	 * @throws REXPMismatchException 
	 * @throws RserveException 
	 */
	public Object evalAutoType(String script, RConnection rconn) 
			throws ScriptException, RserveException, REXPMismatchException {
		REXP rexp = rconn.eval( script );
		Object result = rexp.asNativeJavaObject();
		if ( result != null && result.getClass().isArray() ) {
			int len = Array.getLength( result );
			if ( len == 0)
				// empty vector is handled as null
				result = null;
			else if (len == 1) {
				// Return first element in array
				result = Array.get( result, 0);
				
				// NA -> null conversion
				if ( rexp instanceof REXPInteger ) {
					if ( REXPInteger.NA == (int) result)
						result = null;
				} else if ( rexp instanceof REXPDouble ) {
					if ( REXPDouble.isNA( (double) result) )
						result = null;
				}
			}
		}
		return result;
	}
}
