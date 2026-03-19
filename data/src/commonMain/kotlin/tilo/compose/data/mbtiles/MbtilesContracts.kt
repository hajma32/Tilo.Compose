package tilo.compose.data.mbtiles

interface MbtilesFileProvider {
    fun provideDatabasePath(): String
}

interface MbtilesSqlDriverFactory {
    fun createDriver(databasePath: String): app.cash.sqldelight.db.SqlDriver
}
