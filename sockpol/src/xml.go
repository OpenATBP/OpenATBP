package main

import (
	"encoding/xml"
	"regexp"
)

type SiteControl struct {
	XMLName  xml.Name `xml:"site-control"`
	Policies string   `xml:"permitted-cross-domain-policies,attr"`
}

type AllowAccessFrom struct {
	XMLName xml.Name `xml:"allow-access-from"`
	Domain  string   `xml:"domain,attr"`
}

type AllowHttpRequestHeadersFrom struct {
	XMLName xml.Name `xml:"allow-http-request-headers-from"`
	Domain  string   `xml:"domain,attr"`
	Headers string   `xml:"headers,attr"`
}

type CrossDomainPolicy struct {
	XMLName                     xml.Name                    `xml:"cross-domain-policy"`
	SiteControl                 SiteControl                 `xml:"site-control"`
	AllowAccessFrom             AllowAccessFrom             `xml:"allow-access-from"`
	AllowHttpRequestHeadersFrom AllowHttpRequestHeadersFrom `xml:"allow-http-request-headers-from"`
}

func createPoliciesFile(config IConfig) string {
	var policies string = config.Policies
	var domain string = config.Domain
	var headers string = config.Headers
	var indent bool = config.Indent
	var selfclosing bool = config.Selfclosing
	policy := &CrossDomainPolicy{
		SiteControl:                 SiteControl{Policies: policies},
		AllowAccessFrom:             AllowAccessFrom{Domain: domain},
		AllowHttpRequestHeadersFrom: AllowHttpRequestHeadersFrom{Domain: domain, Headers: headers},
	}
	var content []byte
	var err error
	if indent {
		content, err = xml.MarshalIndent(policy, "", "  ")
	} else {
		content, err = xml.Marshal(policy)
	}
	if err != nil {
		logger.Fatal("Failed to create crossdomain.xml file: %s", err.Error())
	}
	contentStr := xml.Header + string(content)
	// Turn nested tags into self-closing tags
	if selfclosing {
		contentStr = regexp.MustCompile("<(.+)(.*)></.+>").ReplaceAllString(contentStr, "<$1$2/>")
	}
	return contentStr
}
