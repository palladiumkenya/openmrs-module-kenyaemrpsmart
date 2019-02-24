<%
    ui.decorateWith("kenyaemr", "standardPage", [ patient: currentPatient, layout: "sidebar" ])
    def menuItems = [
            [ label: "Back to home", iconProvider: "kenyaui", icon: "buttons/back.png", label: "Back to home", href: ui.pageLink("kenyaemr", "clinician/clinicianViewPatient", [ patient: currentPatient, patientId: currentPatient.patientId]) ]
    ]
%>
<style>
.simple-table {
    border: solid 1px #0a132c;
    border-collapse: collapse;
    border-spacing: 0;
    font: normal 16px Arial, sans-serif;
}
.simple-table th {
    background-color: #DDEFEF;
    border: solid 1px #0a132c;
    color: #336B6B;
    padding: 10px;
    text-align: left;
    text-shadow: 1px 1px 1px #fff;
}
.simple-table td {
    border: solid 1px #0a132c;
    color: #333;
    padding: 14px;
    text-shadow: 1px 1px 1px #fff;
}
</style>

<div class="ke-page-sidebar">
    <div class="ke-panel-frame">
        ${ ui.includeFragment("kenyaui", "widget/panelMenu", [ heading: "Navigation", items: menuItems ]) }
    </div>
</div>

<div class="ke-page-content">

    <div id="program-tabs" class="ke-tabs">
        <div class="ke-tabmenu">
            <div class="ke-tabmenu-item" data-tabid="summaries">P-SMART Summaries</div>

            <div class="ke-tabmenu-item" data-tabid="hivtesting">HIV Testing Data</div>

            <div class="ke-tabmenu-item" data-tabid="immunization">Immunization Data</div>


        </div>

        <div class="ke-tab" data-tabid="summaries">
            <table cellspacing="0" cellpadding="0" width="100%" class="simple-table">
                <tr>
                    <td style="width: 70%; vertical-align: top">

                        <div class="ke-panel-frame">
                            <div class="ke-panel-heading"></div>
                            <div class="ke-panel-content">
                                <table>
                                    <tr>
                                        <th colspan="2" align="left">Statistics</th>
                                    </tr>
                                    <tr>
                                        <td>Total Tests</td>
                                        <td>${summaries.totalTests}</td>
                                    </tr>
                                    <tr>
                                        <td>Total Immunizations</td>
                                        <td>${summaries.totalImmunizations}</td>
                                    </tr>
                                </table>


                            </div>
                    </td>
                </tr>
            </table>
        </div>
        <div class="ke-tab" data-tabid="hivtesting">
            <table cellspacing="0" cellpadding="0" width="100%" class="simple-table">
                <tr>
                    <td style="width: 50%; vertical-align: top">
                        <div class="ke-panel-frame">
                            <div class="ke-panel-heading"></div>

                            <div class="ke-panel-content">
                                <% if (existingTests) { %>
                                <table>
                                <tr>
                                    <td>Date Tested</td>
                                    <td>Result</td>
                                    <td>Test Type</td>
                                    <td>Test Strategy</td>
                                    <td>Test Facility</td>
                                </tr>
                                <% existingTests.each { rel -> %>
                                <tr>
                                        <td>${rel.dateTested}</td>
                                        <td>${rel.result}</td>
                                        <td>${rel.type}</td>
                                        <td>${rel.strategy}</td>
                                        <td>${rel.facility}</td>


                                </tr>
                                <% } %>
                            </table>
                                <% }  else {%>
                                No HIV Test found
                                <% } %>
                            </div>
                        </div>
                    </td>

                </tr>
            </table>
        </div>
        <div class="ke-tab" data-tabid="immunization">
            <table cellspacing="0" cellpadding="0" width="100%">
                <tr>
                    <td style="width: 50%; vertical-align: top">
                        <div class="ke-panel-frame">
                            <div class="ke-panel-heading"></div>

                            <div class="ke-panel-content">
                                <% if (existingImmunizations) { %>
                                <table class="simple-table">
                                <tr>
                                    <td>Date</td>
                                    <td>Vaccination</td>
                                </tr>

                                <% existingImmunizations.each { rel -> %>
                                <tr>
                                    <td>${rel.vaccinationDate}</td>
                                    <td>${rel.vaccination}</td>
                                </tr>

                                <% } %>
                            </table>
                                <% } else {%>
                                No Immunization data found
                                <% } %>
                            </div>
                        </div>

                    </td>
                </tr>
            </table>
        </div>
        <br/>
        <br/>
        <br/>
    </div>
</div>
