package slick.jdbc

import com.typesafe.config.Config
import slick.SlickException
import slick.ast.{FieldSymbol, Node, Select, TableNode}
import slick.basic.Capability
import slick.lifted.{HanaIndex, Index, PrimaryKey}
import slick.relational.HanaTable

trait HanaProfile extends JdbcProfile { profile =>

  override protected def computeCapabilities: Set[Capability] = (super.computeCapabilities
    - JdbcCapabilities.nullableNoDefault)

  override protected[this] def loadProfileConfig: Config = {
    super.loadProfileConfig
  }

  override def createTableDDLBuilder(table: Table[_]): TableDDLBuilder = new TableDDLBuilder(table)

  class TableDDLBuilder(table: Table[_]) extends super.TableDDLBuilder(table) {
    private val sapTable: HanaTable[_] = try {
      table.asInstanceOf[HanaTable[_]]
    } catch {
      case e: Exception => throw new SlickException("The table object is not of type HanaTable")
    }

    override protected def createTable: String = {
      var b = new StringBuilder append ""
      if (sapTable.tableType != null) {
        b = b append s"create ${sapTable.tableType} table " append quoteTableName(tableNode) append " ("
      } else {
        b = b append s"create table " append quoteTableName(tableNode) append " ("
      }
      var first = true
      for (c <- columns) {
        if (first) first = false else b append ","
        c.appendColumn(b)
      }
      addTableOptions(b)
      b.append(")")
      b.toString()
    }

    override protected def createIndex(idx: Index): String = {
      val sapIdx = try {
        idx.asInstanceOf[HanaIndex]
      } catch {
        case e: Exception => throw new SlickException("The index object is not of type HanaIndex")
      }

      val b = new StringBuilder append "create "
      if (sapIdx.unique) b append "unique "
      b append "index " append quoteIdentifier(sapIdx.name) append " on " append quoteTableName(tableNode) append " ("
      addIndexToColumnList(sapIdx.on, b, sapIdx.table.tableName, sapIdx.sort)
      b.append(")")
      b.toString()
    }

    override protected def createPrimaryKey(pk: PrimaryKey): String = {
      if (pk.columns.size > 1)
        throw new SlickException("Table "+tableNode.tableName+" defines multiple primary key columns in "
          + pk.name)

      val sb = new StringBuilder append "alter table " append quoteTableName(tableNode) append " add "
      addPrimaryKey(pk, sb)
      sb.toString()
    }

    def addIndexToColumnList(columns: IndexedSeq[Node], sb: StringBuilder, requiredTableName: String, sort: Seq[String]) = {
      var first = true
      var count = 0
      for(c <- columns) c match {
        case Select(t: TableNode, field: FieldSymbol) =>
          if(first) first = false
          else sb append ","
          sb append quoteIdentifier(field.name) append " " append sort(count)
          if(requiredTableName != t.tableName)
            throw new SlickException("All columns in index must belong to table "+requiredTableName)
          count += 1
        case _ => throw new SlickException("Cannot use column "+c+" in index (only named columns are allowed)")
      }
    }
  }
}

object HanaProfile extends HanaProfile {

}