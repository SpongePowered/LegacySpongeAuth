import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import play.filters.csrf.CSRFFilter

class Filters @Inject()(csrfFilters: CSRFFilter) extends DefaultHttpFilters(csrfFilters)
