//
// SQLTableProcessor
//
// Copyright (C) jextra.net.
//
//  This file is part of the Fauxjo Library.
//
//  The Fauxjo Library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//
//  The Fauxjo Library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//
//  You should have received a copy of the GNU Lesser General Public
//  License along with the Fauxjo Library; if not, write to the Free
//  Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
//  02111-1307 USA.
//

package net.jextra.fauxjo;

import java.sql.*;
import java.util.*;

/**
 * Core Business logic for interacting with a single SQL database table.
 */
public class SQLTableProcessor<T extends Fauxjo> extends AbstractSQLProcessor<T>
{
    // ============================================================
    // Fields
    // ============================================================

    private static final String TABLE_NAME = "TABLE_NAME";
    private static final String COLUMN_NAME = "COLUMN_NAME";
    private static final String DATA_TYPE = "DATA_TYPE";

    private Schema _schema;
    private String _tableName;

    private Coercer _coercer;

    // Key = Lowercase column name (in code known as the "key").
    // Value = Name of column used by the database and SQL type.
    private Map<String, ColumnInfo> _dbColumnInfos;

    // ============================================================
    // Constructors
    // ============================================================

    public SQLTableProcessor( Schema schema, String tableName, Class<T> beanClass )
    {
        super( new ResultSetRecordProcessor<T>( beanClass ) );
        _schema = schema;
        _tableName = tableName;
        _coercer = new Coercer();
    }

    // ============================================================
    // Methods
    // ============================================================

    // ----------
    // public
    // ----------

    public Coercer getCoercer()
    {
        return _coercer;
    }

    /**
     * Convert the bean into an insert statement and execute it.
     */
    @Override
    public boolean insert( Fauxjo bean )
        throws SQLException
    {
        try
        {
            StringBuilder columns = new StringBuilder();
            StringBuilder questionMarks = new StringBuilder();
            List<DataValue> values = new ArrayList<DataValue>();
            HashMap<String, String> generatedKeys = new HashMap<String, String>();

            for ( String key : getDBColumnInfos().keySet() )
            {
                ColumnInfo columnInfo = getDBColumnInfos().get( key );
                Class<?> destClass = SQLTypeMapper.getInstance().getJavaClass( columnInfo.getSQLType() );

                Object val = bean.readValue( key );
                try
                {
                    val = _coercer.coerce( val, destClass );
                }
                catch ( FauxjoException ex )
                {
                    throw new FauxjoException( "Failed to coerce " + getQualifiedName( _tableName ) + "."
                        + columnInfo.getRealName() + " for insert: " + key + ":" + columnInfo.getRealName(), ex );
                }

                // If this is a primary key and it is null, try to get sequence name from
                // annotation and not include this column in insert statement.
                FieldDef fieldDef = getResultSetRecordProcessor().getBeanFieldDefs( bean ).get( key );
                if ( fieldDef == null )
                {
                    continue;
                }
                else if ( fieldDef.isPrimaryKey() && val == null && fieldDef.getPrimaryKeySequenceName() != null )
                {
                    generatedKeys.put( key, fieldDef.getPrimaryKeySequenceName() );
                    continue;
                }

                if ( columns.length() > 0 )
                {
                    columns.append( "," );
                    questionMarks.append( "," );
                }
                columns.append( columnInfo.getRealName() );
                questionMarks.append( "?" );
                values.add( new DataValue( val, columnInfo.getSQLType() ) );
            }
            String sql = "insert into " + getQualifiedName( _tableName ) + " (" + columns + ") values ("
                + questionMarks + ")";
            PreparedStatement statement = getConnection().prepareStatement( sql );
            int propIndex = 1;
            for ( DataValue value : values )
            {
                if ( value.getSqlType() == java.sql.Types.ARRAY )
                {
                    if ( value.getValue() == null )
                    {
                        statement.setNull( propIndex, value.getSqlType() );
                    }
                    else
                    {
                        Array array = getConnection().createArrayOf( "varchar", (Object[]) value.getValue() );
                        statement.setArray( propIndex, array );
                    }
                }
                else
                {
                    statement.setObject( propIndex, value.getValue(), value.getSqlType() );
                }
                propIndex++;
            }
            boolean retVal = statement.execute();

            //
            // Now get generated keys
            //
            for ( String key : generatedKeys.keySet() )
            {
                Statement gkStatement = getConnection().createStatement();
                ResultSet rs = gkStatement.executeQuery( "select currval('"
                    + getQualifiedName( generatedKeys.get( key ) ) + "')" );
                rs.next();
                Object value = rs.getObject( 1 );
                rs.close();
                gkStatement.close();

                bean.writeValue( key, value );
            }

            return retVal;
        }
        catch ( Exception ex )
        {
            if ( ex instanceof FauxjoException )
            {
                throw (FauxjoException) ex;
            }

            throw new FauxjoException( ex );
        }
    }

    /**
     * Convert the bean into an update statement and execute it.
     */
    @Override
    public int update( Fauxjo bean )
        throws SQLException
    {
        try
        {
            StringBuilder setterClause = new StringBuilder();
            StringBuilder whereClause = new StringBuilder();
            List<DataValue> values = new ArrayList<DataValue>();
            List<DataValue> keyValues = new ArrayList<DataValue>();
            for ( String key : getDBColumnInfos().keySet() )
            {
                ColumnInfo columnInfo = getDBColumnInfos().get( key );
                Class<?> destClass = SQLTypeMapper.getInstance().getJavaClass( columnInfo.getSQLType() );

                Object val = bean.readValue( key );
                try
                {
                    val = _coercer.coerce( val, destClass );
                }
                catch ( FauxjoException ex )
                {
                    throw new FauxjoException( "Failed to coerce " + getQualifiedName( _tableName ) + "."
                        + columnInfo.getRealName() + " for insert: " + key + ":" + columnInfo.getRealName(), ex );
                }

                FieldDef fieldDef = getResultSetRecordProcessor().getBeanFieldDefs( bean ).get( key );
                if ( fieldDef != null )
                {
                    if ( fieldDef.isPrimaryKey() )
                    {
                        if ( whereClause.length() > 0 )
                        {
                            whereClause.append( " and " );
                        }
                        whereClause.append( columnInfo.getRealName() + "=?" );
                        keyValues.add( new DataValue( val, columnInfo.getSQLType() ) );
                    }
                    else
                    {
                        if ( setterClause.length() > 0 )
                        {
                            setterClause.append( "," );
                        }
                        setterClause.append( columnInfo.getRealName() + "=?" );
                        values.add( new DataValue( val, columnInfo.getSQLType() ) );
                    }
                }
            }

            String sql = "update " + getQualifiedName( _tableName ) + " set " + setterClause + " where " + whereClause;
            PreparedStatement statement = getConnection().prepareStatement( sql );
            int propIndex = 1;
            for ( DataValue value : values )
            {
                if ( value.getSqlType() == java.sql.Types.ARRAY )
                {
                    if ( value.getValue() == null )
                    {
                        statement.setNull( propIndex, value.getSqlType() );
                    }
                    else
                    {
                        Array array = getConnection().createArrayOf( "varchar", (Object[]) value.getValue() );
                        statement.setArray( propIndex, array );
                    }
                }
                else
                {
                    statement.setObject( propIndex, value.getValue(), value.getSqlType() );
                }
                propIndex++;
            }
            for ( DataValue value : keyValues )
            {
                statement.setObject( propIndex, value.getValue(), value.getSqlType() );
                propIndex++;
            }

            return statement.executeUpdate();
        }
        catch ( Exception ex )
        {
            if ( ex instanceof FauxjoException )
            {
                throw (FauxjoException) ex;
            }

            throw new FauxjoException( ex );
        }
    }

    /**
     * Convert the bean into an delete statement and execute it.
     */
    @Override
    public boolean delete( Fauxjo bean )
        throws SQLException
    {
        try
        {
            StringBuilder whereClause = new StringBuilder();
            List<DataValue> primaryKeyValues = new ArrayList<DataValue>();
            Map<String, FieldDef> fieldDefs = getResultSetRecordProcessor().getBeanFieldDefs( bean );
            for ( String key : fieldDefs.keySet() )
            {
                FieldDef fieldDef = fieldDefs.get( key );
                if ( fieldDef == null || !fieldDef.isPrimaryKey() )
                {
                    continue;
                }

                ColumnInfo columnInfo = getDBColumnInfos().get( key );
                Class<?> destClass = SQLTypeMapper.getInstance().getJavaClass( columnInfo.getSQLType() );

                Object val = bean.readValue( key );
                val = _coercer.coerce( val, destClass );

                if ( whereClause.length() > 0 )
                {
                    whereClause.append( " and " );
                }
                whereClause.append( columnInfo.getRealName() + "=?" );
                primaryKeyValues.add( new DataValue( val, columnInfo.getSQLType() ) );
            }

            String sql = "delete from " + getQualifiedName( _tableName ) + " where " + whereClause;
            PreparedStatement statement = getConnection().prepareStatement( sql );
            int propIndex = 1;
            for ( DataValue value : primaryKeyValues )
            {
                statement.setObject( propIndex, value.getValue(), value.getSqlType() );
                propIndex++;
            }
            return statement.execute();
        }
        catch ( Exception ex )
        {
            if ( ex instanceof FauxjoException )
            {
                throw (FauxjoException) ex;
            }

            throw new FauxjoException( ex );
        }
    }

    @Override
    public String buildBasicSelect( String clause )
    {
        String c = "";
        if ( clause != null && !clause.trim().isEmpty() )
        {
            c = clause;
        }
        return "select * from " + _schema.getQualifiedName( _tableName ) + " " + c;
    }

    @Override
    public Schema getSchema()
    {
        return _schema;
    }

    public String getTableName()
    {
        return _tableName;
    }

    /**
     * Get the next value from a sequence.
     */
    public Long getNextKey( String sequenceName )
        throws SQLException
    {
        if ( sequenceName == null || sequenceName.isEmpty() )
        {
            throw new FauxjoException( "Sequence name must not be null or empty." );
        }

        PreparedStatement getKey = getConnection().prepareStatement(
            "select nextval('" + getQualifiedName( sequenceName ) + "')" );

        ResultSet rs = getKey.executeQuery();
        rs.next();
        return rs.getLong( 1 );
    }

    @Override
    public T convertResultSetRow( ResultSet rs )
        throws SQLException
    {
        return getResultSetRecordProcessor().convertResultSetRow( rs );
    }

    // ----------
    // protected
    // ----------

    protected Connection getConnection()
        throws SQLException
    {
        return _schema.getConnection();
    }

    // ----------
    // private
    // ----------

    private String getQualifiedName( String name )
    {
        return _schema.getQualifiedName( name );
    }

    private Map<String, ColumnInfo> getDBColumnInfos()
        throws SQLException
    {
        if ( _dbColumnInfos == null )
        {
            cacheColumnInfos( true );
        }

        return _dbColumnInfos;
    }

    /**
     * This is a really slow method to call when it actually gets the meta data.
     */
    private void cacheColumnInfos( boolean throwException )
        throws SQLException
    {
        String realTableName = getRealTableName( _tableName );

        //
        // If the table does not actually exist optionally throw exception.
        //
        if ( realTableName == null )
        {
            if ( throwException )
            {
                throw new FauxjoException( String.format( "Table %s does not exist.", getQualifiedName( _tableName ) ) );
            }
            else
            {
                return;
            }
        }

        _dbColumnInfos = new HashMap<String, ColumnInfo>();

        ResultSet rs = getConnection().getMetaData().getColumns( null, _schema.getSchemaName(), realTableName, null );
        while ( rs.next() )
        {
            String realName = rs.getString( COLUMN_NAME );
            Integer type = rs.getInt( DATA_TYPE );

            _dbColumnInfos.put( realName.toLowerCase(), new ColumnInfo( realName, type ) );
        }
        rs.close();
    }

    /**
     * This takes a case insensitive tableName and searches for it in the connection's meta data to find the connections
     * case sensitive tableName.
     */
    private String getRealTableName( String tableName )
        throws SQLException
    {
        ArrayList<String> tableTypes = new ArrayList<String>();

        ResultSet rs = getConnection().getMetaData().getTableTypes();
        while ( rs.next() )
        {
            if ( rs.getString( 1 ).toLowerCase().contains( "table" ) )
            {
                tableTypes.add( rs.getString( 1 ) );
            }
        }
        rs.close();

        rs = getConnection().getMetaData().getTables( null, _schema.getSchemaName(), null,
            tableTypes.toArray( new String[0] ) );

        while ( rs.next() )
        {
            if ( rs.getString( TABLE_NAME ).equalsIgnoreCase( tableName ) )
            {
                String name = rs.getString( TABLE_NAME );
                rs.close();
                return name;
            }
        }
        rs.close();

        return null;
    }

    // ============================================================
    // Inner Classes
    // ============================================================

    public static class ColumnInfo
    {
        private String _realName;
        private int _sqlType;

        public ColumnInfo( String realName, int sqlType )
        {
            _realName = realName;
            _sqlType = sqlType;
        }

        public String getRealName()
        {
            return _realName;
        }

        public void setRealName( String realName )
        {
            _realName = realName;
        }

        public int getSQLType()
        {
            return _sqlType;
        }

        public void setSQLType( int sqlType )
        {
            _sqlType = sqlType;
        }
    }

    private class DataValue
    {
        private Object _value;
        private int _sqlType;

        public DataValue( Object value, int sqlType )
        {
            _value = value;
            _sqlType = sqlType;
        }

        public Object getValue()
        {
            return _value;
        }

        public int getSqlType()
        {
            return _sqlType;
        }
    }
}
