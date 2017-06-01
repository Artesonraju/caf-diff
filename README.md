# CAF-DIFF

Mailing list automation for CAF Chambéry outings

## Install

### Configuration

#### MailChimp :
- Create de MailChimp account
- Create a list
- Create a custom MailChimp template ("Code your Own") importing mailchimp-template.html
- Create a campaign with the custom template (campaign to be duplicated)

#### Caf-diff :
In a directory, create a file named "config.edn"
```clojure
{:mailchimp-key "your mailchimp api key"
 :mailchimp-api "your mailchimp api url"
 :template-id "your custom template id"
 :default-campaign-id "your campaign id"}
 :sending-hour 4))))))
 :outing-properties #{"MASSIF :" "DÉNIVELÉ :" "... other properties to be retrieved from the website"}))))))
```
Create a subdirectory named "data" and put one file for each list :
```clojure
{:title "your mail title and object"
 :list-id "your mailchimp list id"
 :rss "your rss feed url"}
```
## Run

Install [Boot-clj](https://github.com/boot-clj/boot#install), run ```boot prod``` to generate a jar, launch it with your directory path as argument

## License

MIT
