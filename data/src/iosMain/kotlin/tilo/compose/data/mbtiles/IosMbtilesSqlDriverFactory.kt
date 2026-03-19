package tilo.compose.data.mbtiles

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import tilo.compose.data.mbtiles.db.MbtilesDatabase

class IosMbtilesSqlDriverFactory : MbtilesSqlDriverFactory {
    override fun createDriver(databasePath: String): SqlDriver {
        return NativeSqliteDriver(
            schema = MbtilesDatabase.Schema,
            name = databasePath
        )
    }
}

