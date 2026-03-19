package tilo.compose.data.mbtiles

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.io.File
import tilo.compose.data.mbtiles.db.MbtilesDatabase

class AndroidMbtilesSqlDriverFactory(
    private val context: Context
) : MbtilesSqlDriverFactory {
    override fun createDriver(databasePath: String): SqlDriver {
        return AndroidSqliteDriver(
            schema = MbtilesDatabase.Schema,
            context = context,
            name = File(databasePath).name
        )
    }
}
