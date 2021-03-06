import com.virtusa.gto.nyql.model.units.AParam

/**
 * @author IWEERARATHNA
 */
[
        $DSL.dbFunction ("MyProcedure",
                [
                        $DSL.PARAM("firstP", AParam.ParamScope.IN, "mapper1"),
                        $DSL.PARAM("secondP", AParam.ParamScope.IN, "mapper2"),
                        $DSL.PARAM("thirdP", AParam.ParamScope.OUT, "mapper3")
                ]
        ),
        [
            mysql:  ["{ CALL MyProcedure(?, ?, ?) }", ["firstP", "secondP", "thirdP"]],
            pg:     ["", ["firstP", "secondP", "thirdP"]]
        ]
]