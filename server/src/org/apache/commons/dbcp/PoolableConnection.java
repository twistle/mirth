/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.dbcp;

import java.sql.Connection;
import java.sql.SQLException;
import org.apache.commons.pool.ObjectPool;

/**
 * MIRTH-3262
 * Backporting the fix for https://issues.apache.org/jira/browse/DBCP-391 into the 1.4 release.
 * This fix actually introduced a new bug that was only fixed on the DBCP2 branch at revision 1568674.
 * Therefore I've also backported the fix for the new bug as well. It will (probably) work.
 * If we ever update the DBCP jar or switch to a different connection pool then this class will need to be removed.
 * 
 * A delegating connection that, rather than closing the underlying
 * connection, returns itself to an {@link ObjectPool} when
 * closed.
 * 
 * 
 *
 * @author Rodney Waldhoff
 * @author Glenn L. Nielsen
 * @author James House
 * @version $Revision: 758745 $ $Date: 2009-03-26 10:02:20 -0700 (Thu, 26 Mar 2009) $
 */
public class PoolableConnection extends DelegatingConnection {
    /** The pool to which I should return. */
    // TODO: Correct use of the pool requires that this connection is only every returned to the pool once.
    protected ObjectPool _pool = null;

    /**
     *
     * @param conn my underlying connection
     * @param pool the pool to which I should return when closed
     */
    public PoolableConnection(Connection conn, ObjectPool pool) {
        super(conn);
        _pool = pool;
    }

    /**
     *
     * @param conn my underlying connection
     * @param pool the pool to which I should return when closed
     * @param config the abandoned configuration settings
     */
    public PoolableConnection(Connection conn, ObjectPool pool, AbandonedConfig config) {
        super(conn, config);
        _pool = pool;
    }
    
    
    @Override
    protected void passivate() throws SQLException {
        super.passivate();
        _closed = true;
    }


    @Override
    public boolean isClosed() throws SQLException {
        if (_closed) {
            return true;
        }

        if (getDelegateInternal().isClosed()) {
            // Something has gone wrong. The underlying connection has been
            // closed without the connection being returned to the pool. Return
            // it now.
            close();
            return true;
        }

        return false;
    }


    /**
     * Returns me to my pool.
     */
     public synchronized void close() throws SQLException {
        if (_closed) {
            // already closed
            return;
        }

        boolean isUnderlyingConectionClosed;
        try {
            isUnderlyingConectionClosed = _conn.isClosed();
        } catch (SQLException e) {
            try {
                _pool.invalidateObject(this); // XXX should be guarded to happen at most once
            } catch(IllegalStateException ise) {
                // pool is closed, so close the connection
                passivate();
                getInnermostDelegate().close();
            } catch (Exception ie) {
                // DO NOTHING the original exception will be rethrown
            }
            throw (SQLException) new SQLException("Cannot close connection (isClosed check failed)").initCause(e);
        }

        if (!isUnderlyingConectionClosed) {
            // Normal close: underlying connection is still open, so we
            // simply need to return this proxy to the pool
            try {
                _pool.returnObject(this); // XXX should be guarded to happen at most once
            } catch(IllegalStateException e) {
                // pool is closed, so close the connection
                passivate();
                getInnermostDelegate().close();
            } catch(SQLException e) {
                throw e;
            } catch(RuntimeException e) {
                throw e;
            } catch(Exception e) {
                throw (SQLException) new SQLException("Cannot close connection (return to pool failed)").initCause(e);
            }
        } else {
            // Abnormal close: underlying connection closed unexpectedly, so we
            // must destroy this proxy
            try {
                _pool.invalidateObject(this); // XXX should be guarded to happen at most once
            } catch(IllegalStateException e) {
                // pool is closed, so close the connection
                passivate();
                getInnermostDelegate().close();
            } catch (Exception e) {
                throw (SQLException) new SQLException("Cannot close connection (invalidating pooled object failed)").initCause(e);
            }
        }
    }

    /**
     * Actually close my underlying {@link Connection}.
     */
    public void reallyClose() throws SQLException {
        super.close();
    }
}

