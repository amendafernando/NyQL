/**
 * @author IWEERARATHNA
 */
[
        $DSL.update {
            TARGET (Address.alias("ad"))
            SET {
                EQ (ad.address2, IFNULL(ad.address2, STR("")))
            }
        },
        [
            mysql: "UPDATE `Address` ad SET ad.address2 = IFNULL(ad.address2, \"\")"
        ],

        $DSL.update {
            TARGET (Address.alias("ad"))
            SET {
                EQ (ad.address2, IFNOTNULL(ad.address2, STR("")))
            }
        },
        [
            mysql: "UPDATE `Address` ad SET ad.address2 = CASE WHEN ad.address2 IS NOT NULL THEN \"\" ELSE ad.address2 END"
        ],

        $DSL.update {
            TARGET (UserVisits.alias("uv"))
            SET {
                EQ (uv.visits, uv.visits + 1)
            }
        },
        [
                mysql: "UPDATE `UserVisits` uv SET uv.visits = (uv.visits + 1)"
        ],
]